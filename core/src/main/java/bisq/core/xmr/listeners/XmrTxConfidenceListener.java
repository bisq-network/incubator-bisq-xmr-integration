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

package bisq.core.xmr.listeners;

import bisq.core.xmr.jsonrpc.result.MoneroTx;

public class XmrTxConfidenceListener {
    private final String txID;

    public XmrTxConfidenceListener(String txID) {
        this.txID = txID;
    }

    public String getTxID() {
        return txID;
    }

    //TODO(niyid) Replace with equivalent to TransactionConfidence - probably an int
    public void onTransactionConfidenceChanged(MoneroTx confidence) {
    }
}
