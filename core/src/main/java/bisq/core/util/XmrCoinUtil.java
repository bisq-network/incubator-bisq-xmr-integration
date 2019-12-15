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

package bisq.core.util;

import bisq.common.util.MathUtils;
import bisq.core.xmr.XmrCoin;

public class XmrCoinUtil {

    // Get the fee per amount
    public static XmrCoin getFeePerXmr(XmrCoin feePerXmr, XmrCoin amount) {
        double feePerXmrAsDouble = feePerXmr != null ? (double) feePerXmr.value : 0;
        double amountAsDouble = amount != null ? (double) amount.value : 0;
        double xmrAsDouble = (double) XmrCoin.COIN.value;
        double fact = amountAsDouble / xmrAsDouble;
        return XmrCoin.valueOf(Math.round(feePerXmrAsDouble * fact));
    }

    public static XmrCoin minCoin(XmrCoin a, XmrCoin b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static XmrCoin maxCoin(XmrCoin a, XmrCoin b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static double getFeePerByte(XmrCoin miningFee, int txSize) {
        double value = miningFee != null ? miningFee.value : 0;
        return MathUtils.roundDouble((value / (double) txSize), 2);
    }

    /**
     * @param value Xmr amount to be converted to percent value. E.g. 0.01 BTC is 1% (of 1 BTC)
     * @return The percentage value as double (e.g. 1% is 0.01)
     */
    public static double getAsPercentPerXmr(XmrCoin value) {
        return getAsPercentPerXmr(value, XmrCoin.COIN);
    }

    /**
     * @param part Xmr amount to be converted to percent value, based on total value passed.
     *              E.g. 0.1 BTC is 25% (of 0.4 BTC)
     * @param total Total Xmr amount the percentage part is calculated from
     *
     * @return The percentage value as double (e.g. 1% is 0.01)
     */
    public static double getAsPercentPerXmr(XmrCoin part, XmrCoin total) {
        double asDouble = part != null ? (double) part.value : 0;
        double btcAsDouble = total != null ? (double) total.value : 1;
        return MathUtils.roundDouble(asDouble / btcAsDouble, 4);
    }

    /**
     * @param percent       The percentage value as double (e.g. 1% is 0.01)
     * @param amount        The amount as XmrCoin for the percentage calculation
     * @return The percentage as XmrCoin (e.g. 1% of 1 BTC is 0.01 BTC)
     */
    public static XmrCoin getPercentOfAmountAsCoin(double percent, XmrCoin amount) {
        double amountAsDouble = amount != null ? (double) amount.value : 0;
        return XmrCoin.valueOf(Math.round(percent * amountAsDouble));
    }
}
