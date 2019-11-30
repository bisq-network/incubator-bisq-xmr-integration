package bisq.desktop.main.offer.xmr;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.offer.XmrOfferUtil;
import bisq.core.user.Preferences;
import bisq.core.xmr.XmrCoin;
import bisq.core.xmr.wallet.XmrWalletRpcWrapper;

public class XmrMakerFeeProvider {

    public XmrCoin getMakerFee(XmrWalletRpcWrapper xmrWalletWrapper, Preferences preferences, XmrCoin amount, String price) {
        return XmrOfferUtil.getMakerFee(xmrWalletWrapper, preferences, amount, price);
    }

    public XmrCoin getMakerFee(BsqWalletService bsqWalletService, Preferences preferences, XmrCoin amount, String price) {
        return XmrOfferUtil.getMakerFee(bsqWalletService, preferences, amount, price);
    }
}
