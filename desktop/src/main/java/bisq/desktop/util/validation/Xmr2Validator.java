/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.util.validation;

import bisq.core.xmr.wallet.XmrRestrictions;
import bisq.core.locale.Res;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.util.XmrBSFormatter;
import bisq.core.xmr.XmrCoin;

import javax.inject.Inject;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
//TODO(niyid) Merge with XmrValidator
public class Xmr2Validator extends NumberValidator {

    protected final XmrBSFormatter formatter;

    @Nullable
    @Setter
    protected XmrCoin minValue = XmrCoin.DUST;

    @Nullable
    @Setter
    protected XmrCoin maxValue;

    @Nullable
    @Setter
    @Getter
    protected XmrCoin maxTradeLimit;
    
    private PriceFeedService priceFeedService;
    private MarketPrice xmrMarketPrice;

    @Inject
    public Xmr2Validator(XmrBSFormatter formatter, PriceFeedService priceFeedService) {
        this.formatter = formatter;
        this.priceFeedService = priceFeedService;
        
        setMaxValue(XmrCoin.parseCoin("1000"));
        xmrMarketPrice = priceFeedService.getMarketPrice("XMR");
    }


    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid) {
            input = cleanInput(input);
            result = validateIfNumber(input);
        }

        if (result.isValid) {
            result = validateIfNotZero(input)
                    .and(validateIfNotNegative(input))
                    .and(validateIfNotFractionalXmrValue(input))
                    .and(validateIfNotExceedsMaxXmrValue(input))
                    .and(validateIfNotExceedsMaxTradeLimit(input))
                    .and(validateIfNotUnderMinValue(input))
                    .and(validateIfAboveDust(input));
        }

        return result;
    }
    
    public void setMaxValue(@NotNull XmrCoin maxValue) {
    	this.maxValue = maxValue;
    }

    protected ValidationResult validateIfAboveDust(String input) {
        try {
        	double btcToXmrRate = xmrMarketPrice.getPrice();
            final XmrCoin coin = XmrCoin.parseCoin(input);
            if (XmrRestrictions.isAboveDust(coin, btcToXmrRate))
                return new ValidationResult(true);
            else
                return new ValidationResult(false, Res.get("validation.amountBelowDust",
                        formatter.formatCoinWithCode(XmrRestrictions.getMinNonDustOutput(btcToXmrRate))));
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    protected ValidationResult validateIfNotFractionalXmrValue(String input) {
        try {
            BigDecimal bd = new BigDecimal(input);
            final BigDecimal satoshis = bd.movePointRight(12);
            if (satoshis.scale() > 0)
                return new ValidationResult(false, Res.get("validation.xmr.fraction"));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    protected ValidationResult validateIfNotExceedsMaxXmrValue(String input) {
        try {
            final XmrCoin coin = XmrCoin.parseCoin(input);
            if (maxValue != null && coin.compareTo(maxValue) > 0) {
                return new ValidationResult(false, Res.get("validation.xmr.toLarge", formatter.formatCoinWithCode(maxValue)));
            } else {
                return new ValidationResult(true);
            }
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    protected ValidationResult validateIfNotExceedsMaxTradeLimit(String input) {
        try {
            final XmrCoin coin = XmrCoin.parseCoin(input);
            if (maxTradeLimit != null && coin.compareTo(maxTradeLimit) > 0)
                return new ValidationResult(false, Res.get("validation.xmr.exceedsMaxTradeLimit", formatter.formatCoinWithCode(maxTradeLimit)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    protected ValidationResult validateIfNotUnderMinValue(String input) {
        try {
            final XmrCoin coin = XmrCoin.parseCoin(input);
            if (minValue != null && coin.compareTo(minValue) < 0)
                return new ValidationResult(false, Res.get("validation.xmr.toSmall", formatter.formatCoinWithCode(minValue)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }
}
