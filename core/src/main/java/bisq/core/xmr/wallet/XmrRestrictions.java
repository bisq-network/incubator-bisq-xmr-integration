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

package bisq.core.xmr.wallet;

import bisq.core.btc.wallet.Restrictions;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.xmr.XmrCoin;

import javax.annotation.Nullable;

import org.bitcoinj.core.Coin;

public class XmrRestrictions {
    public static XmrCoin getMinNonDustOutput(double xmrToBtcRate) {
    	return XmrCoin.fromCoin2XmrCoin(Restrictions.getMinNonDustOutput(), String.valueOf(xmrToBtcRate));
    }

    private static XmrCoin minNonDustOutput;

    public static boolean isAboveDust(XmrCoin amount, double xmrToBtcRate) {
        return amount.compareTo(getMinNonDustOutput(xmrToBtcRate)) >= 0;
    }

    public static boolean isDust(XmrCoin amount, double xmrToBtcRate) {
        return !isAboveDust(amount, xmrToBtcRate);
    }

    public static XmrCoin getMinTradeAmount(double xmrToBtcRate) {
    	Coin coin = Restrictions.getMinTradeAmount();
    	return XmrCoin.fromCoin2XmrCoin(coin, String.valueOf(xmrToBtcRate));
    }

    public static double getDefaultBuyerSecurityDepositAsPercent(@Nullable PaymentAccount paymentAccount) {
        if (PaymentAccountUtil.isCryptoCurrencyAccount(paymentAccount))
            return 0.02; // 2% of trade amount.
        else
            return 0.1; // 10% of trade amount.
    }

    public static double getMinBuyerSecurityDepositAsPercent(@Nullable PaymentAccount paymentAccount) {
        if (PaymentAccountUtil.isCryptoCurrencyAccount(paymentAccount))
            return 0.005; // 0.5% of trade amount.
        else
            return 0.05; // 5% of trade amount.
    }

    public static double getMaxBuyerSecurityDepositAsPercent(@Nullable PaymentAccount paymentAccount) {
        if (PaymentAccountUtil.isCryptoCurrencyAccount(paymentAccount))
            return 0.2; // 20% of trade amount. For a 1 BTC trade it is about 800 USD @ 4000 USD/BTC
        else
            return 0.5; // 50% of trade amount. For a 1 BTC trade it is about 2000 USD @ 4000 USD/BTC
    }

    // We use MIN_BUYER_SECURITY_DEPOSIT as well as lower bound in case of small trade amounts.
    // So 0.0005 BTC is the min. buyer security deposit even with amount of 0.0001 BTC and 0.05% percentage value.
    public static XmrCoin getMinBuyerSecurityDepositAsCoin(double xmrToBtcRate) {
    	Coin coin = Restrictions.getMinBuyerSecurityDepositAsCoin();
    	return XmrCoin.fromCoin2XmrCoin(coin, String.valueOf(xmrToBtcRate));
    }


    public static double getSellerSecurityDepositAsPercent() {
        return 0.05; // 5% of trade amount.
    }

    public static XmrCoin getMinSellerSecurityDepositAsCoin(double xmrToBtcRate) {
    	Coin coin = Restrictions.getMinSellerSecurityDepositAsCoin();
    	return XmrCoin.fromCoin2XmrCoin(coin, String.valueOf(xmrToBtcRate));
    }
}
