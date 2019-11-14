package bisq.desktop.main.offer.xmr;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.offer.OfferUtil;
import bisq.core.user.Preferences;

import org.bitcoinj.core.Coin;

public class XmrMakerFeeProvider {
    public Coin getMakerFee(BsqWalletService bsqWalletService, Preferences preferences, Coin amount) {
        return OfferUtil.getMakerFee(bsqWalletService, preferences, amount);
    }
}
