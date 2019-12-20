package bisq.desktop.main.offer.xmr;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.offer.XmrOfferUtil;
import bisq.core.user.Preferences;
import bisq.core.xmr.XmrCoin;

public class XmrMakerFeeProvider {

    public XmrCoin getMakerFee(BsqWalletService bsqWalletService, Preferences preferences, XmrCoin amount, String btcConversionRate, String bsqConversionRate) {
        return XmrOfferUtil.getMakerFee(bsqWalletService, preferences, amount, btcConversionRate, bsqConversionRate);
    }
}
