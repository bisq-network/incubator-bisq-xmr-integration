/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.offer;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.payment.F2FAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.XmrFeeService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;
import bisq.core.util.XmrCoinUtil;
import bisq.core.xmr.wallet.XmrRestrictions;
import bisq.core.xmr.wallet.XmrWalletRpcWrapper;
import bisq.core.xmr.XmrCoin;
import bisq.network.p2p.P2PService;

import bisq.common.app.Capabilities;
import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import com.google.common.annotations.VisibleForTesting;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class holds utility methods for the creation of an Offer.
 * Most of these are extracted here because they are used both in the GUI and in the API.
 * <p>
 * Long-term there could be a GUI-agnostic OfferService which provides these and other functionalities to both the
 * GUI and the API.
 */
@Slf4j
public class XmrOfferUtil {

    /**
     * Given the direction, is this a BUY?
     *
     * @param direction
     * @return
     */
    public static boolean isBuyOffer(OfferPayload.Direction direction) {
        return direction == OfferPayload.Direction.BUY;
    }

    /**
     * Returns the makerFee as XmrCoin, this can be priced in XMR or BSQ.
     *
     * @param xmrWalletRpcWrapper
     * @param preferences          preferences are used to see if the user indicated a preference for paying fees in BTC
     * @param amount
     * @return
     */
    @Nullable
    public static XmrCoin getMakerFee(XmrWalletRpcWrapper xmrWalletRpcWrapper, Preferences preferences, XmrCoin amount, String xmrConversionRateAsString) {
        boolean isCurrencyForMakerFeeXmr = isCurrencyForMakerFeeXmr(preferences, xmrWalletRpcWrapper, amount, xmrConversionRateAsString);
        return getXmrMakerFee(isCurrencyForMakerFeeXmr, amount, xmrConversionRateAsString);
    }

    /**
     * Returns the makerFee as XmrCoin, this can be priced in XMR or BSQ.
     *
     * @param bsqWalletService
     * @param preferences          preferences are used to see if the user indicated a preference for paying fees in BTC
     * @param amount
     * @return
     */
    @Nullable
    public static XmrCoin getMakerFee(BsqWalletService bsqWalletService, Preferences preferences, XmrCoin amount, String xmrConversionRateAsString) {
        boolean isCurrencyForMakerFeeXmr = isCurrencyForMakerFeeXmr(preferences, bsqWalletService, amount, xmrConversionRateAsString);
        return getXmrMakerFee(isCurrencyForMakerFeeXmr, amount, xmrConversionRateAsString);
    }
    

    /**
     * Calculates the maker fee for the given amount, marketPrice and marketPriceMargin.
     *
     * @param isCurrencyForMakerFeeXmr
     * @param amount
     * @return
     */
    @Nullable
    public static XmrCoin getMakerFee(boolean isCurrencyForMakerFeeXmr, @Nullable XmrCoin amount, String xmrConversionRateAsString) {
        if (amount != null) {
            XmrCoin feePerXmr = XmrCoinUtil.getFeePerXmr(XmrFeeService.getMakerFeePerXmr(isCurrencyForMakerFeeXmr, xmrConversionRateAsString), amount);
            return XmrCoinUtil.maxCoin(feePerXmr, XmrFeeService.getMinMakerFee(isCurrencyForMakerFeeXmr, xmrConversionRateAsString));
        } else {
            return null;
        }
    }

    /**
     * Calculates the maker fee for the given amount, marketPrice and marketPriceMargin.
     *
     * @param isCurrencyForMakerFeeXmr
     * @param amount
     * @return
     */
    @Nullable
    public static XmrCoin getXmrMakerFee(boolean isCurrencyForMakerFeeXmr, @Nullable XmrCoin amount, String xmrConversionRateAsString) {
        if (amount != null) {
            XmrCoin feePerXmr = XmrCoinUtil.getFeePerXmr(XmrFeeService.getMakerFeePerXmr(isCurrencyForMakerFeeXmr, xmrConversionRateAsString), amount);
            return XmrCoinUtil.maxCoin(feePerXmr, XmrFeeService.getMinMakerFee(isCurrencyForMakerFeeXmr, xmrConversionRateAsString));
        } else {
            return null;
        }
    }

    /**
     * Checks if the maker fee should be paid in BTC, this can be the case due to user preference or because the user
     * doesn't have enough BSQ.
     *
     * @param preferences
     * @param bsqWalletService
     * @param amount
     * @return
     */
    public static boolean isCurrencyForMakerFeeXmr(Preferences preferences,
                                                   BsqWalletService bsqWalletService,
                                                   XmrCoin amount, String xmrConversionRateAsString) {
        boolean payFeeInXmr = preferences.isPayFeeInXmr();
        boolean bsqForFeeAvailable = isBsqForMakerFeeAvailable(bsqWalletService, amount, xmrConversionRateAsString);
        return payFeeInXmr || !bsqForFeeAvailable;
    }

    /**
     * Checks if the maker fee should be paid in XMR, this can be the case due to user preference or because the user
     * doesn't have enough BSQ.
     *
     * @param preferences
     * @param bsqWalletService
     * @param amount
     * @return
     */
    public static boolean isCurrencyForMakerFeeXmr(Preferences preferences,
                                                   XmrWalletRpcWrapper xmrWalletRpcWrapper,
                                                   XmrCoin amount, String xmrConversionRateAsString) {
        boolean payFeeInXmr = preferences.isPayFeeInXmr();
        boolean bsqForFeeAvailable = isBsqForMakerFeeAvailable(xmrWalletRpcWrapper, amount, xmrConversionRateAsString);
        return payFeeInXmr || !bsqForFeeAvailable;
    }

    /**
     * Checks if the available BSQ balance is sufficient to pay for the offer's maker fee.
     *
     * @param bsqWalletService
     * @param amount
     * @return
     */
    public static boolean isBsqForMakerFeeAvailable(BsqWalletService bsqWalletService, @Nullable XmrCoin amount, String xmrConversionRateAsString) {
        XmrCoin availableBalance = XmrCoin.fromCoin2XmrCoin(bsqWalletService.getAvailableConfirmedBalance(), String.valueOf(xmrConversionRateAsString));
        XmrCoin makerFee = getMakerFee(false, amount, xmrConversionRateAsString);

        // If we don't know yet the maker fee (amount is not set) we return true, otherwise we would disable BSQ
        // fee each time we open the create offer screen as there the amount is not set.
        if (makerFee == null)
            return true;

        return !availableBalance.subtract(makerFee).isNegative();
    }


    /**
     * Checks if the available BSQ balance is sufficient to pay for the offer's maker fee.
     *
     * @param xmrWalletRpcWrapper
     * @param amount
     * @return
     */
    public static boolean isBsqForMakerFeeAvailable(XmrWalletRpcWrapper xmrWalletRpcWrapper, @Nullable XmrCoin amount, String xmrConversionRateAsString) {
    	//TODO(niyid) Check for accuracy needed
    	if(xmrWalletRpcWrapper.isXmrWalletRpcRunning()) {
            XmrCoin availableBalance = xmrWalletRpcWrapper.getBalance();
            XmrCoin makerFee = getMakerFee(false, amount, xmrConversionRateAsString);

            // If we don't know yet the maker fee (amount is not set) we return true, otherwise we would disable BSQ
            // fee each time we open the create offer screen as there the amount is not set.
            if (makerFee == null)
                return true;

            return !availableBalance.subtract(makerFee).isNegative();
    	} else {
    		return false;
    	}
    }

    @Nullable
    public static XmrCoin getTakerFee(boolean isCurrencyForTakerFeeXmr, @Nullable XmrCoin amount, String xmrConversionRateAsString) {
        if (amount != null) {
            XmrCoin feePerXmr = XmrCoinUtil.getFeePerXmr(XmrFeeService.getTakerFeePerXmr(isCurrencyForTakerFeeXmr, xmrConversionRateAsString), amount);
            return XmrCoinUtil.maxCoin(feePerXmr, XmrFeeService.getMinTakerFee(isCurrencyForTakerFeeXmr, xmrConversionRateAsString));
        } else {
            return null;
        }
    }

    public static boolean isCurrencyForTakerFeeXmr(Preferences preferences,
                                                   BsqWalletService bsqWalletService,
                                                   XmrCoin amount, String price) {
        boolean payFeeInXmr = preferences.isPayFeeInXmr();
        boolean bsqForFeeAvailable = isBsqForTakerFeeAvailable(bsqWalletService, XmrCoin.fromXmrCoin2Coin(amount, "BSQ", String.valueOf(price)), price);
        return payFeeInXmr || !bsqForFeeAvailable;
    }

    public static boolean isBsqForTakerFeeAvailable(BsqWalletService bsqWalletService, @Nullable Coin amount, String xmrConversionRateAsString) {
        Coin availableBalance = bsqWalletService.getAvailableConfirmedBalance();
        Coin takerFee = OfferUtil.getTakerFee(false, amount);

        // If we don't know yet the maker fee (amount is not set) we return true, otherwise we would disable BSQ
        // fee each time we open the create offer screen as there the amount is not set.
        if (takerFee == null)
            return true;

        return !availableBalance.subtract(takerFee).isNegative();
    }

    public static Volume getRoundedFiatVolume(Volume volumeByAmount) {
        // We want to get rounded to 1 unit of the fiat currency, e.g. 1 EUR.
        return getAdjustedFiatVolume(volumeByAmount, 1);
    }

    public static Volume getAdjustedVolumeForHalCash(Volume volumeByAmount) {
        // EUR has precision 4 and we want multiple of 10 so we divide by 100000 then
        // round and multiply with 10
        return getAdjustedFiatVolume(volumeByAmount, 10);
    }

    /**
     *
     * @param volumeByAmount      The volume generated from an amount
     * @param factor              The factor used for rounding. E.g. 1 means rounded to units of 1 EUR, 10 means rounded to 10 EUR...
     * @return The adjusted Fiat volume
     */
    @VisibleForTesting
    static Volume getAdjustedFiatVolume(Volume volumeByAmount, int factor) {
        // Fiat currencies use precision 4 and we want multiple of factor so we divide by 10000 * factor then
        // round and multiply with factor
        long roundedVolume = Math.round((double) volumeByAmount.getValue() / (10000d * factor)) * factor;
        // Smallest allowed volume is factor (e.g. 10 EUR or 1 EUR,...)
        roundedVolume = Math.max(factor, roundedVolume);
        return Volume.parse(String.valueOf(roundedVolume), volumeByAmount.getCurrencyCode());
    }

    /**
     * Calculate the possibly adjusted amount for {@code amount}, taking into account the
     * {@code price} and {@code maxTradeLimit} and {@code factor}.
     *
     * @param amount            Monero amount which is a candidate for getting rounded.
     * @param price             Price used in relation to that amount.
     * @param maxTradeLimit     The max. trade limit of the users account, in satoshis.
     * @return The adjusted amount
     */
    public static XmrCoin getRoundedFiatAmount(XmrCoin amount, Price price, long maxTradeLimit, double xmrToBtcRate) {
        return getAdjustedAmount(amount, price, maxTradeLimit, 1, xmrToBtcRate);
    }

    public static XmrCoin getAdjustedAmountForHalCash(XmrCoin amount, Price price, long maxTradeLimit, double xmrToBtcRate) {
        return getAdjustedAmount(amount, price, maxTradeLimit, 10, xmrToBtcRate);
    }

    /**
     * Calculate the possibly adjusted amount for {@code amount}, taking into account the
     * {@code price} and {@code maxTradeLimit} and {@code factor}.
     *
     * @param amount            Monero amount which is a candidate for getting rounded.
     * @param price             Price used in relation to that amount.
     * @param maxTradeLimit     The max. trade limit of the users account, in satoshis.
     * @param factor            The factor used for rounding. E.g. 1 means rounded to units of
     *                          1 EUR, 10 means rounded to 10 EUR, etc.
     * @return The adjusted amount
     */
    @VisibleForTesting
    static XmrCoin getAdjustedAmount(XmrCoin amount, Price price, long maxTradeLimit, int factor, double xmrToBtcRate) {
    	Coin btcAmount = XmrCoin.fromXmrCoin2Coin(amount, "BTC", String.valueOf(xmrToBtcRate));
    	XmrCoin coin = XmrCoin.fromCoin2XmrCoin(OfferUtil.getAdjustedAmount(btcAmount, price, maxTradeLimit, factor), String.valueOf(xmrToBtcRate));

    	return coin;
    }

    public static Optional<Volume> getFeeInUserFiatCurrency(Coin makerFee, boolean isCurrencyForMakerFeeXmr,
                                                            Preferences preferences, PriceFeedService priceFeedService,
                                                            BsqFormatter bsqFormatter) {
        String countryCode = preferences.getUserCountry().code;
        String userCurrencyCode = CurrencyUtil.getCurrencyByCountryCode(countryCode).getCode();
        return getFeeInUserFiatCurrency(makerFee,
                isCurrencyForMakerFeeXmr,
                userCurrencyCode,
                priceFeedService,
                bsqFormatter);
    }

    public static Optional<Volume> getFeeInUserFiatCurrency(XmrCoin xmrMakerFee, boolean isCurrencyForMakerFeeBtc,
                                                            Preferences preferences, PriceFeedService priceFeedService,
                                                            BsqFormatter bsqFormatter) {
    	MarketPrice xmrMarketPrice = priceFeedService.getMarketPrice("XMR");
    	MarketPrice bsqMarketPrice = priceFeedService.getMarketPrice("BSQ");
    	double bsqToXmrRate = bsqMarketPrice.getPrice() / xmrMarketPrice.getPrice();
    	Coin makerFee = XmrCoin.fromXmrCoin2Coin(xmrMakerFee, "BSQ", String.valueOf(bsqToXmrRate));
        String countryCode = preferences.getUserCountry().code;
        String userCurrencyCode = CurrencyUtil.getCurrencyByCountryCode(countryCode).getCode();
        return getFeeInUserFiatCurrency(makerFee,
                isCurrencyForMakerFeeBtc,
                userCurrencyCode,
                priceFeedService,
                bsqFormatter);
    }

    public static Optional<Volume> getFeeInUserFiatCurrency(Coin makerFee, boolean isCurrencyForMakerFeeBtc,
                                                            String userCurrencyCode, PriceFeedService priceFeedService,
                                                            BsqFormatter bsqFormatter) {
        // We use the users currency derived from his selected country.
        // We don't use the preferredTradeCurrency from preferences as that can be also set to an altcoin.

        MarketPrice marketPrice = priceFeedService.getMarketPrice(userCurrencyCode);
        if (marketPrice != null && makerFee != null) {
            long marketPriceAsLong = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(marketPrice.getPrice(), Fiat.SMALLEST_UNIT_EXPONENT));
            Price userCurrencyPrice = Price.valueOf(userCurrencyCode, marketPriceAsLong);

            if (isCurrencyForMakerFeeBtc) {
                return Optional.of(userCurrencyPrice.getVolumeByAmount(makerFee));
            } else {
                Optional<Price> optionalBsqPrice = priceFeedService.getBsqPrice();
                if (optionalBsqPrice.isPresent()) {
                    Price bsqPrice = optionalBsqPrice.get();
                    String inputValue = bsqFormatter.formatCoin(makerFee);
                    Volume makerFeeAsVolume = Volume.parse(inputValue, "BSQ");
                    Coin requiredVolume = bsqPrice.getAmountByVolume(makerFeeAsVolume);
                    return Optional.of(userCurrencyPrice.getVolumeByAmount(requiredVolume));
                } else {
                    return Optional.empty();
                }
            }
        } else {
            return Optional.empty();
        }
    }


    public static Map<String, String> getExtraDataMap(AccountAgeWitnessService accountAgeWitnessService,
                                                      ReferralIdService referralIdService,
                                                      PaymentAccount paymentAccount,
                                                      String currencyCode,
                                                      Preferences preferences, PriceFeedService priceFeedService) {
        Map<String, String> extraDataMap = new HashMap<>();
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            String myWitnessHashAsHex = accountAgeWitnessService.getMyWitnessHashAsHex(paymentAccount.getPaymentAccountPayload());
            extraDataMap.put(OfferPayload.ACCOUNT_AGE_WITNESS_HASH, myWitnessHashAsHex);
        }

        if (referralIdService.getOptionalReferralId().isPresent()) {
            extraDataMap.put(OfferPayload.REFERRAL_ID, referralIdService.getOptionalReferralId().get());
        }

        if (paymentAccount instanceof F2FAccount) {
            extraDataMap.put(OfferPayload.F2F_CITY, ((F2FAccount) paymentAccount).getCity());
            extraDataMap.put(OfferPayload.F2F_EXTRA_INFO, ((F2FAccount) paymentAccount).getExtraInfo());
        }

        extraDataMap.put(OfferPayload.CAPABILITIES, Capabilities.app.toStringList());

        //TODO(niyid) Check if the MarketPrice values for XMR and BSQ are accurate
        MarketPrice xmrMarketPrice = priceFeedService.getMarketPrice("XMR");
        extraDataMap.put(OfferPayload.BTC_TO_XMR_RATE, String.valueOf((1.0 / xmrMarketPrice.getPrice())));
        log.info("Using XMR Market Price of: Currency -> {}, Price -> {}, Date -> {}, External -> {}", xmrMarketPrice.getCurrencyCode(), (1.0 / xmrMarketPrice.getPrice()), Date.from(Instant.ofEpochSecond(xmrMarketPrice.getTimestampSec())), xmrMarketPrice.isExternallyProvidedPrice());

        MarketPrice bsqMarketPrice = priceFeedService.getMarketPrice("BSQ");
        extraDataMap.put(OfferPayload.BSQ_TO_XMR_RATE, String.valueOf(bsqMarketPrice.getPrice() / xmrMarketPrice.getPrice()));
        log.info("Using BSQ Market Price of: Currency -> {}, Price -> {}, Date -> {}, External -> {}, BSQ/XMR Rate -> {}", bsqMarketPrice.getCurrencyCode(), (1.0 / bsqMarketPrice.getPrice()), Date.from(Instant.ofEpochSecond(bsqMarketPrice.getTimestampSec())), bsqMarketPrice.isExternallyProvidedPrice(), (bsqMarketPrice.getPrice() / xmrMarketPrice.getPrice()));

        return extraDataMap.isEmpty() ? null : extraDataMap;
    }

    public static void validateOfferData(FilterManager filterManager,
                                         P2PService p2PService,
                                         double buyerSecurityDeposit,
                                         PaymentAccount paymentAccount,
                                         String currencyCode,
                                         XmrCoin makerFeeAsCoin) {
        checkNotNull(makerFeeAsCoin, "makerFee must not be null");
        checkNotNull(p2PService.getAddress(), "Address must not be null");
        checkArgument(buyerSecurityDeposit <= XmrRestrictions.getMaxBuyerSecurityDepositAsPercent(paymentAccount),
                "securityDeposit must not exceed " +
                        XmrRestrictions.getMaxBuyerSecurityDepositAsPercent(paymentAccount));
        checkArgument(buyerSecurityDeposit >= XmrRestrictions.getMinBuyerSecurityDepositAsPercent(paymentAccount),
                "securityDeposit must not be less than " +
                        XmrRestrictions.getMinBuyerSecurityDepositAsPercent(paymentAccount));
        checkArgument(!filterManager.isCurrencyBanned(currencyCode),
                Res.get("offerbook.warning.currencyBanned"));
        checkArgument(!filterManager.isPaymentMethodBanned(paymentAccount.getPaymentMethod()),
                Res.get("offerbook.warning.paymentMethodBanned"));
    }

    // TODO no code duplication found in UI code (added for API)
   /* public static XmrCoin getFundsNeededForOffer(XmrCoin tradeAmount, XmrCoin buyerSecurityDeposit, OfferPayload.Direction direction) {
        boolean buyOffer = isBuyOffer(direction);
        XmrCoin needed = buyOffer ? buyerSecurityDeposit : XmrRestrictions.getSellerSecurityDeposit();
        if (!buyOffer)
            needed = needed.add(tradeAmount);

        return needed;
    }*/
}
