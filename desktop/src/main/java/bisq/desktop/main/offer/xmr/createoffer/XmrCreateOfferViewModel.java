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

package bisq.desktop.main.offer.xmr.createoffer;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.main.offer.xmr.XmrMutableOfferViewModel;
import bisq.desktop.util.validation.AltcoinValidator;
import bisq.desktop.util.validation.BsqValidator;
import bisq.desktop.util.validation.Xmr2Validator;
import bisq.desktop.util.validation.FiatPriceValidator;
import bisq.desktop.util.validation.FiatVolumeValidator;
import bisq.desktop.util.validation.SecurityDepositValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.XmrBSFormatter;
import bisq.core.xmr.wallet.XmrWalletRpcWrapper;
import bisq.core.util.BsqFormatter;

import com.google.inject.Inject;

class XmrCreateOfferViewModel extends XmrMutableOfferViewModel<XmrCreateOfferDataModel> implements ViewModel {

    @Inject
    public XmrCreateOfferViewModel(XmrCreateOfferDataModel dataModel,
                                FiatVolumeValidator fiatVolumeValidator,
                                FiatPriceValidator fiatPriceValidator,
                                AltcoinValidator altcoinValidator,
                                Xmr2Validator btcValidator,
                                BsqValidator bsqValidator,
                                SecurityDepositValidator securityDepositValidator,
                                PriceFeedService priceFeedService,
                                AccountAgeWitnessService accountAgeWitnessService,
                                Navigation navigation,
                                Preferences preferences,
                                XmrBSFormatter xmrFormatter,
                                BsqFormatter bsqFormatter,
                                XmrWalletRpcWrapper xmrWalletWrapper) {
        super(dataModel,
                fiatVolumeValidator,
                fiatPriceValidator,
                altcoinValidator,
                btcValidator,
                bsqValidator,
                securityDepositValidator,
                priceFeedService,
                accountAgeWitnessService,
                navigation,
                preferences,
                xmrFormatter,
                bsqFormatter,
                xmrWalletWrapper);
    }
}
