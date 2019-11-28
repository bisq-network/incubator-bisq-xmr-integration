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

import bisq.core.xmr.XmrCoin;
import bisq.core.xmr.jsonrpc.result.Address;
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

    @Getter
    protected final BooleanProperty isXmrWalletFunded = new SimpleBooleanProperty();
    @Getter
    protected final ObjectProperty<XmrCoin> totalToPayAsCoin = new SimpleObjectProperty<>();
    @Getter
    protected final ObjectProperty<XmrCoin> balance = new SimpleObjectProperty<>();
    @Getter
    protected final ObjectProperty<XmrCoin> missingCoin = new SimpleObjectProperty<>(XmrCoin.ZERO);
    @Getter
    protected final BooleanProperty showWalletFundedNotification = new SimpleBooleanProperty();
    @Getter
    protected XmrCoin totalAvailableBalance;
    //TODO(niyid) Replace with Address class in monero-java-lite library
    protected Address addressEntry;
    protected boolean useSavingsWallet;
    

    public XmrOfferDataModel(XmrWalletRpcWrapper xmrWalletWrapper) {
        this.xmrWalletWrapper = xmrWalletWrapper;
    }

    protected void updateBalance() {
    	//TODO(niyid) tradeWallet is multisig wallet created for this trade using the tradeId
    	if(xmrWalletWrapper != null && addressEntry != null) {
            XmrCoin tradeWalletBalance = xmrWalletWrapper.getBalanceForAddress(addressEntry.getAddress());
            log.info("XmrOfferDataModel => {0}", tradeWalletBalance.getValue());
            if (useSavingsWallet) {
            	// savingWallet is primary wallet       	
                XmrCoin savingWalletBalance = xmrWalletWrapper.getBalance();
                totalAvailableBalance = savingWalletBalance.add(tradeWalletBalance);
                if (totalToPayAsCoin.get() != null) {
                    if (totalAvailableBalance.compareTo(totalToPayAsCoin.get()) > 0)
                        balance.set(totalToPayAsCoin.get());
                    else
                        balance.set(totalAvailableBalance);
                }
            } else {
                balance.set(tradeWalletBalance);
            }
            balance.set(tradeWalletBalance);
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
    
    protected void createMultisigWallet(String offerId) {
    	addressEntry = offerId != null ? xmrWalletWrapper.getOrCreateAddressEntry(offerId) : xmrWalletWrapper.getOrCreateAddressEntry();
    }
}
