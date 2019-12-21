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

package bisq.desktop.main.offer.xmr;

import bisq.desktop.common.model.ActivatableDataModel;

import bisq.core.xmr.wallet.XmrWalletRpcWrapper;

import org.bitcoinj.core.Coin;
import org.bitcoinj.wallet.Wallet.BalanceType;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.util.BsqFormatter;
import bisq.core.xmr.XmrCoin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import lombok.Getter;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
public abstract class XmrOfferDataModel extends ActivatableDataModel {
    protected final XmrWalletRpcWrapper xmrWalletWrapper;

    protected final BsqWalletService bsqWalletService;
    @Getter
    protected final BooleanProperty isXmrWalletFunded = new SimpleBooleanProperty();
    @Getter
    protected final ObjectProperty<XmrCoin> totalToPayAsCoin = new SimpleObjectProperty<>();
    @Getter
    protected final ObjectProperty<XmrCoin> balance = new SimpleObjectProperty<>();
    @Getter
    protected final ObjectProperty<Coin> balanceBsq = new SimpleObjectProperty<>();
    @Getter
    protected final ObjectProperty<XmrCoin> missingCoin = new SimpleObjectProperty<>(XmrCoin.ZERO);
    @Getter
    protected final BooleanProperty showWalletFundedNotification = new SimpleBooleanProperty();
    @Getter
    protected XmrCoin totalAvailableBalance;
    //TODO(niyid) Replace with Address class in monero-java-lite library
    protected boolean useSavingsWallet;
    protected MarketPrice xmrMarketPrice;
    protected MarketPrice bsqMarketPrice;
    protected final PriceFeedService priceFeedService;
    protected double bsqToXmrRate;
    protected double btcToXmrRate;
    private BsqFormatter bsqFormatter;

    public XmrOfferDataModel(BsqWalletService bsqWalletService, XmrWalletRpcWrapper xmrWalletWrapper, PriceFeedService priceFeedService, BsqFormatter bsqFormatter) {
        this.bsqWalletService = bsqWalletService;
        this.xmrWalletWrapper = xmrWalletWrapper;
        this.priceFeedService = priceFeedService;
        this.bsqFormatter = bsqFormatter;
        updateRateAndBalance();
    }
    
    private void updateRateAndBalance() {
        xmrMarketPrice = priceFeedService.getMarketPrice("XMR");
        bsqMarketPrice = priceFeedService.getMarketPrice("BSQ");
        bsqToXmrRate = xmrMarketPrice.getPrice() / bsqMarketPrice.getPrice();
        Coin bsqBalance = bsqWalletService.getAvailableConfirmedBalance();
        balanceBsq.set(bsqBalance);
        totalAvailableBalance = XmrCoin.fromCoin2XmrCoin(bsqBalance, String.valueOf(bsqToXmrRate));
//        log.info("Trade Wallet Balance => {} ({}) Rate={}", totalAvailableBalance.toFriendlyString(), bsqFormatter.formatAmountWithGroupSeparatorAndCode(bsqBalance), bsqToXmrRate);
    }

    protected void updateBalance() {
    	if(bsqWalletService != null) {
    		updateRateAndBalance();
            if (useSavingsWallet) {
//            	// savingWallet is BSQ wallet       	
//                XmrCoin savingWalletBalance = XmrCoin.fromCoin2XmrCoin(bsqWalletService.getBalance(BalanceType.AVAILABLE_SPENDABLE), String.valueOf(bsqToXmrRate));
                if (totalToPayAsCoin.get() != null) {
                    if (totalAvailableBalance.compareTo(totalToPayAsCoin.get()) > 0)
                        balance.set(totalToPayAsCoin.get());
                    else
                        balance.set(totalAvailableBalance);
                }
            } else {
                balance.set(totalAvailableBalance);
            }
            balance.set(totalAvailableBalance);
            if (totalToPayAsCoin.get() != null) {
                missingCoin.set(totalToPayAsCoin.get().subtract(balance.get()));
                if (missingCoin.get().isNegative())
                    missingCoin.set(XmrCoin.ZERO);
            }

            isXmrWalletFunded.set(isBalanceSufficient(balance.get()));
            if (totalToPayAsCoin.get() != null && isXmrWalletFunded.get() && !showWalletFundedNotification.get()) {
                showWalletFundedNotification.set(true);
            }
    	}
    }

    private boolean isBalanceSufficient(XmrCoin balance) {
        return totalToPayAsCoin.get() != null && balance.compareTo(totalToPayAsCoin.get()) >= 0;
    }
}
