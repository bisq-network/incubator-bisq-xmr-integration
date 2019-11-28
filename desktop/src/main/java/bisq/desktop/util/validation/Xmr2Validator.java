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
import bisq.core.util.BSFormatter;
import bisq.core.util.XmrBSFormatter;
import bisq.core.xmr.XmrCoin;

import javax.inject.Inject;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

public class Xmr2Validator extends NumberValidator {

    protected final XmrBSFormatter formatter;

    @Nullable
    @Setter
    protected XmrCoin minValue;

    @Nullable
    @Setter
    protected XmrCoin maxValue;

    @Nullable
    @Setter
    @Getter
    protected XmrCoin maxTradeLimit;

    @Inject
    public Xmr2Validator(XmrBSFormatter formatter) {
        this.formatter = formatter;
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

    protected ValidationResult validateIfAboveDust(String input) {
        try {
            final XmrCoin coin = XmrCoin.parseCoin(input);
            if (XmrRestrictions.isAboveDust(coin))
                return new ValidationResult(true);
            else
                return new ValidationResult(false, Res.get("validation.amountBelowDust",
                        formatter.formatCoinWithCode(XmrRestrictions.getMinNonDustOutput())));
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
            if (maxValue != null && coin.compareTo(maxValue) > 0)
                return new ValidationResult(false, Res.get("validation.xmr.toLarge", formatter.formatCoinWithCode(maxValue)));
            else
                return new ValidationResult(true);
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
