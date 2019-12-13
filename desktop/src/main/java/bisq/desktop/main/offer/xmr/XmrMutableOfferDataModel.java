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

import bisq.desktop.Navigation;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.xmr.wallet.XmrRestrictions;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.XmrOfferUtil;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.CryptoCurrencyAccount;
import bisq.core.payment.HalCashAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.provider.fee.XmrFeeService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.handlers.TransactionResultHandler;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.BsqFormatter;
import bisq.core.util.XmrBSFormatter;
import bisq.core.xmr.wallet.XmrWalletRpcWrapper;
import bisq.core.xmr.wallet.listeners.WalletUiListener;
import bisq.core.util.XmrCoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.util.MathUtils;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import bisq.core.xmr.XmrCoin;
import bisq.core.xmr.jsonrpc.result.Address;
import bisq.core.xmr.jsonrpc.result.MoneroTx;
import bisq.core.xmr.listeners.XmrBalanceListener;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import com.google.common.collect.Lists;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class XmrMutableOfferDataModel extends XmrOfferDataModel implements BsqBalanceListener {
    protected final OpenOfferManager openOfferManager;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final Preferences preferences;
    private final XmrWalletRpcWrapper xmrWalletWrapper;
    protected final User user;
    private final KeyRing keyRing;
    private final P2PService p2PService;
    protected final PriceFeedService priceFeedService;
    final String shortOfferId;
    private final FilterManager filterManager;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final XmrFeeService feeService;
    private final TxFeeEstimationService txFeeEstimationService;
    private final ReferralIdService referralIdService;
    private final XmrBSFormatter xmrFormatter;
    private final BsqFormatter bsqFormatter;
    private XmrMakerFeeProvider makerFeeProvider;
    private final Navigation navigation;
    private final String offerId;
    private final XmrBalanceListener xmrBalanceListener;
    private final SetChangeListener<PaymentAccount> paymentAccountsChangeListener;

    protected OfferPayload.Direction direction;
    protected TradeCurrency tradeCurrency;
    protected final StringProperty tradeCurrencyCode = new SimpleStringProperty();
    protected final BooleanProperty useMarketBasedPrice = new SimpleBooleanProperty();
    //final BooleanProperty isMainNet = new SimpleBooleanProperty();
    //final BooleanProperty isFeeFromFundingTxSufficient = new SimpleBooleanProperty();

    // final ObjectProperty<XmrCoin> feeFromFundingTxProperty = new SimpleObjectProperty(XmrCoin.NEGATIVE_SATOSHI);
    protected final ObjectProperty<XmrCoin> amount = new SimpleObjectProperty<>();
    protected final ObjectProperty<XmrCoin> minAmount = new SimpleObjectProperty<>();
    protected final ObjectProperty<Price> price = new SimpleObjectProperty<>();
    protected final ObjectProperty<Volume> volume = new SimpleObjectProperty<>();
    protected final ObjectProperty<Volume> minVolume = new SimpleObjectProperty<>();

    // Percentage value of buyer security deposit. E.g. 0.01 means 1% of trade amount
    protected final DoubleProperty buyerSecurityDeposit = new SimpleDoubleProperty();
    protected final DoubleProperty sellerSecurityDeposit = new SimpleDoubleProperty();

    protected final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();

    protected PaymentAccount paymentAccount;
    protected boolean isTabSelected;
    protected double marketPriceMargin = 0;
    private XmrCoin txFeeFromXmrFeeService = XmrCoin.ZERO;
    private boolean marketPriceAvailable;
    private int feeTxSize = TxFeeEstimationService.TYPICAL_TX_WITH_1_INPUT_SIZE;
    protected boolean allowAmountUpdate = true;

    //TODO(niyid) Replace BtcWalletService functions with XmrWalletRpcWrapper functions; then completely remove BtcWalletService
    
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public XmrMutableOfferDataModel(OpenOfferManager openOfferManager,
    							 BtcWalletService btcWalletService,
                                 BsqWalletService bsqWalletService,
                                 XmrWalletRpcWrapper xmrWalletWrapper,
                                 Preferences preferences,
                                 User user,
                                 KeyRing keyRing,
                                 P2PService p2PService,
                                 PriceFeedService priceFeedService,
                                 FilterManager filterManager,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 XmrFeeService feeService,
                                 TxFeeEstimationService txFeeEstimationService,
                                 ReferralIdService referralIdService,
                                 XmrBSFormatter xmrFormatter,
                                 BsqFormatter bsqFormatter,
                                 XmrMakerFeeProvider makerFeeProvider,
                                 Navigation navigation) {
        super(bsqWalletService, xmrWalletWrapper, priceFeedService, bsqFormatter);

        this.openOfferManager = openOfferManager;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.xmrWalletWrapper = xmrWalletWrapper;
        this.preferences = preferences;
        this.user = user;
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.filterManager = filterManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.feeService = feeService;
        this.txFeeEstimationService = txFeeEstimationService;
        this.referralIdService = referralIdService;
        this.xmrFormatter = xmrFormatter;
        this.bsqFormatter = bsqFormatter;
        this.makerFeeProvider = makerFeeProvider;
        this.navigation = navigation;

        offerId = Utilities.getRandomPrefix(5, 8) + "-" +
                UUID.randomUUID().toString() + "-" +
                Version.VERSION.replace(".", "");
        shortOfferId = Utilities.getShortId(offerId);

        useMarketBasedPrice.set(preferences.isUsePercentageBasedPrice());
        buyerSecurityDeposit.set(preferences.getBuyerSecurityDepositAsPercent(null));
        sellerSecurityDeposit.set(XmrRestrictions.getSellerSecurityDepositAsPercent());
        
        log.info("Using XMR Market Price of: Currency -> {}, Price -> {}, Date -> {}, External -> {}", xmrMarketPrice.getCurrencyCode(), (1.0 / xmrMarketPrice.getPrice()), Date.from(Instant.ofEpochSecond(xmrMarketPrice.getTimestampSec())), xmrMarketPrice.isExternallyProvidedPrice());
        log.info("Using BSQ Market Price of: Currency -> {}, Price -> {}, Date -> {}, External -> {}", bsqMarketPrice.getCurrencyCode(), (1.0 / bsqMarketPrice.getPrice()), Date.from(Instant.ofEpochSecond(bsqMarketPrice.getTimestampSec())), bsqMarketPrice.isExternallyProvidedPrice());
        bsqToXmrRate = bsqMarketPrice.getPrice() / xmrMarketPrice.getPrice();
        btcToXmrRate = xmrMarketPrice.getPrice();
        log.info("BSQ => XMR is {}", bsqToXmrRate);
        log.info("BTC => XMR is {}", btcToXmrRate);
        xmrBalanceListener = new XmrBalanceListener() {
            @Override
            public void onBalanceChanged(XmrCoin balance, MoneroTx tx) {
                updateBalance();

               /* if (isMainNet.get()) {
                    SettableFuture<XmrCoin> future = blockchainService.requestFee(tx.getHashAsString());
                    Futures.addCallback(future, new FutureCallback<XmrCoin>() {
                        public void onSuccess(XmrCoin fee) {
                            UserThread.execute(() -> feeFromFundingTxProperty.set(fee));
                        }

                        public void onFailure(@NotNull Throwable throwable) {
                            UserThread.execute(() -> new Popup<>()
                                    .warning("We did not get a response for the request of the mining fee used " +
                                            "in the funding transaction.\n\n" +
                                            "Are you sure you used a sufficiently high fee of at least " +
                                            formatter.formatCoinWithCode(FeePolicy.getMinRequiredFeeForFundingTx()) + "?")
                                    .actionButtonText("Yes, I used a sufficiently high fee.")
                                    .onAction(() -> feeFromFundingTxProperty.set(FeePolicy.getMinRequiredFeeForFundingTx()))
                                    .closeButtonText("No. Let's cancel that payment.")
                                    .onClose(() -> feeFromFundingTxProperty.set(XmrCoin.ZERO))
                                    .show());
                        }
                    });
                }*/
            }
        };

        paymentAccountsChangeListener = change -> fillPaymentAccounts();
    }

    @Override
    public void activate() {
        addListeners();

        if (isTabSelected)
            priceFeedService.setCurrencyCode(tradeCurrencyCode.get());

        updateBalance();
    }

    @Override
    protected void deactivate() {
        removeListeners();
    }

    private void addListeners() {
        xmrWalletWrapper.addBalanceListener(xmrBalanceListener);
        bsqWalletService.addBsqBalanceListener(this);
        user.getPaymentAccountsAsObservable().addListener(paymentAccountsChangeListener);
    }

    private void removeListeners() {
    	xmrWalletWrapper.removeBalanceListener(xmrBalanceListener);
        bsqWalletService.removeBsqBalanceListener(this);
        user.getPaymentAccountsAsObservable().removeListener(paymentAccountsChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // called before activate()
    public boolean initWithData(OfferPayload.Direction direction, TradeCurrency tradeCurrency) {
        this.direction = direction;
        this.tradeCurrency = tradeCurrency;

        fillPaymentAccounts();

        PaymentAccount account;

        PaymentAccount lastSelectedPaymentAccount = getPreselectedPaymentAccount();
        if (lastSelectedPaymentAccount != null &&
                lastSelectedPaymentAccount.getTradeCurrencies().contains(tradeCurrency) &&
                user.getPaymentAccounts() != null &&
                user.getPaymentAccounts().stream().anyMatch(paymentAccount -> paymentAccount.getId().equals(lastSelectedPaymentAccount.getId()))) {
            account = lastSelectedPaymentAccount;
        } else {
            account = user.findFirstPaymentAccountWithCurrency(tradeCurrency);
        }

        if (account != null) {
            this.paymentAccount = account;
        } else {
            Optional<PaymentAccount> paymentAccountOptional = paymentAccounts.stream().findAny();
            if (paymentAccountOptional.isPresent()) {
                this.paymentAccount = paymentAccountOptional.get();

            } else {
                log.warn("PaymentAccount not available. Should never get called as in offer view you should not be able to open a create offer view");
                return false;
            }
        }

        setTradeCurrencyFromPaymentAccount(paymentAccount);
        tradeCurrencyCode.set(this.tradeCurrency.getCode());

        priceFeedService.setCurrencyCode(tradeCurrencyCode.get());

        // We request to get the actual estimated fee
        requestTxFee(null);

        // Set the default values (in rare cases if the fee request was not done yet we get the hard coded default values)
        // But offer creation happens usually after that so we should have already the value from the estimation service.
        txFeeFromXmrFeeService = feeService.getTxFee(feeTxSize);

        calculateVolume();
        calculateTotalToPay();
        updateBalance();

        return true;
    }

    protected PaymentAccount getPreselectedPaymentAccount() {
        return preferences.getSelectedPaymentAccountForCreateOffer();
    }

    void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
        if (isTabSelected)
            priceFeedService.setCurrencyCode(tradeCurrencyCode.get());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("ConstantConditions")
    Offer createAndGetOffer() {
        boolean useMarketBasedPriceValue = isUseMarketBasedPriceValue();
        long priceAsLong = price.get() != null && !useMarketBasedPriceValue ? price.get().getValue() : 0L;
        String currencyCode = tradeCurrencyCode.get();
        boolean isCryptoCurrency = CurrencyUtil.isCryptoCurrency(currencyCode);
        String baseCurrencyCode = isCryptoCurrency ? currencyCode : Res.getBaseCurrencyCode();
        String counterCurrencyCode = isCryptoCurrency ? Res.getBaseCurrencyCode() : currencyCode;

        double marketPriceMarginParam = useMarketBasedPriceValue ? marketPriceMargin : 0;
        long amountAsLong = this.amount.get() != null ? this.amount.get().getValue() : 0L;
        long minAmountAsLong = this.minAmount.get() != null ? this.minAmount.get().getValue() : 0L;

        List<String> acceptedCountryCodes = PaymentAccountUtil.getAcceptedCountryCodes(paymentAccount);
        List<String> acceptedBanks = PaymentAccountUtil.getAcceptedBanks(paymentAccount);
        String bankId = PaymentAccountUtil.getBankId(paymentAccount);
        String countryCode = PaymentAccountUtil.getCountryCode(paymentAccount);

        long maxTradeLimit = getMaxTradeLimit();
        long maxTradePeriod = paymentAccount.getMaxTradePeriod();

        // reserved for future use cases
        // Use null values if not set
        boolean isPrivateOffer = false;
        boolean useAutoClose = false;
        boolean useReOpenAfterAutoClose = false;
        long lowerClosePrice = 0;
        long upperClosePrice = 0;
        String hashOfChallenge = null;

        XmrCoin makerFeeAsCoin = getMakerFee();

        Map<String, String> extraDataMap = XmrOfferUtil.getExtraDataMap(accountAgeWitnessService,
                referralIdService,
                paymentAccount,
                currencyCode,
                preferences,
                priceFeedService);

        XmrOfferUtil.validateOfferData(filterManager,
                p2PService,
                buyerSecurityDeposit.get(),
                paymentAccount,
                currencyCode,
                makerFeeAsCoin);

        OfferPayload offerPayload = new OfferPayload(offerId,
                new Date().getTime(),
                p2PService.getAddress(),
                keyRing.getPubKeyRing(),
                OfferPayload.Direction.valueOf(direction.name()),
                priceAsLong,
                marketPriceMarginParam,
                useMarketBasedPriceValue,
                amountAsLong,
                minAmountAsLong,
                baseCurrencyCode,
                counterCurrencyCode,
                Lists.newArrayList(user.getAcceptedArbitratorAddresses()),
                Lists.newArrayList(user.getAcceptedMediatorAddresses()),
                paymentAccount.getPaymentMethod().getId(),
                paymentAccount.getId(),
                null,
                countryCode,
                acceptedCountryCodes,
                bankId,
                acceptedBanks,
                Version.VERSION,
                btcWalletService.getLastBlockSeenHeight(),
                XmrCoin.fromXmrCoin2Coin(txFeeFromXmrFeeService, "BSQ", String.valueOf(bsqToXmrRate)).value,
                XmrCoin.fromXmrCoin2Coin(makerFeeAsCoin, "BSQ", String.valueOf(bsqToXmrRate)).value,
                false,//isCurrencyForMakerFeeBtc=false This means fee Coin values are in BSQ and not BTC
                XmrCoin.fromXmrCoin2Coin(getBuyerSecurityDepositAsCoin(), "BSQ", String.valueOf(bsqToXmrRate)).value,
                XmrCoin.fromXmrCoin2Coin(getSellerSecurityDepositAsCoin(), "BSQ", String.valueOf(bsqToXmrRate)).value,
                maxTradeLimit,
                maxTradePeriod,
                useAutoClose,
                useReOpenAfterAutoClose,
                upperClosePrice,
                lowerClosePrice,
                isPrivateOffer,
                hashOfChallenge,
                extraDataMap,
                Version.TRADE_PROTOCOL_VERSION);
        Offer offer = new Offer(offerPayload);
        offer.setPriceFeedService(priceFeedService);
        return offer;
    }

    // This works only if we have already funds in the wallet
    public void estimateTxSize() {
        XmrCoin reservedFundsForOffer = getSecurityDeposit();
        if (!isBuyOffer())
            reservedFundsForOffer = reservedFundsForOffer.add(amount.get());

        Tuple2<Coin, Integer> estimatedFeeAndTxSize = txFeeEstimationService.getEstimatedFeeAndTxSizeForMaker(XmrCoin.fromXmrCoin2Coin(reservedFundsForOffer, "BSQ", String.valueOf(bsqToXmrRate)),
                XmrCoin.fromXmrCoin2Coin(getMakerFee(), "BSQ", String.valueOf(bsqToXmrRate)));
        txFeeFromXmrFeeService = XmrCoin.fromCoin2XmrCoin(estimatedFeeAndTxSize.first, String.valueOf(xmrMarketPrice.getPrice()));
        feeTxSize = estimatedFeeAndTxSize.second;
    }

    void onPlaceOffer(Offer offer, TransactionResultHandler resultHandler) {
        checkNotNull(getMakerFee(), "makerFee must not be null");

        XmrCoin reservedFundsForOffer = getSecurityDeposit();
        if (!isBuyOffer())
            reservedFundsForOffer = reservedFundsForOffer.add(amount.get());

        //TODO(niyid) Here the reserved funds for offer could be in BSQ from the BSQ wallet but reserved funds are actually in BTC
        //TODO(niyid) useSavingsWallet should be true
        //TODO(niyid) pass XMR-to-BTC conversion rate
        openOfferManager.placeOffer(offer,
                XmrCoin.fromXmrCoin2Coin(reservedFundsForOffer, "BTC", String.valueOf(xmrMarketPrice.getPrice())),
                useSavingsWallet,
                resultHandler,
                log::error);
    }

    void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        if (paymentAccount != null && !this.paymentAccount.equals(paymentAccount)) {
            volume.set(null);
            minVolume.set(null);
            price.set(null);
            marketPriceMargin = 0;
            preferences.setSelectedPaymentAccountForCreateOffer(paymentAccount);
            this.paymentAccount = paymentAccount;

            setTradeCurrencyFromPaymentAccount(paymentAccount);

            buyerSecurityDeposit.set(preferences.getBuyerSecurityDepositAsPercent(getPaymentAccount()));

            if (amount.get() != null) {
                this.amount.set(XmrCoin.valueOf(Math.min(amount.get().value, getMaxTradeLimit())));
            }
        }
    }

    private void setTradeCurrencyFromPaymentAccount(PaymentAccount paymentAccount) {
        if (!paymentAccount.getTradeCurrencies().contains(tradeCurrency)) {
            if (paymentAccount.getSelectedTradeCurrency() != null)
                tradeCurrency = paymentAccount.getSelectedTradeCurrency();
            else if (paymentAccount.getSingleTradeCurrency() != null)
                tradeCurrency = paymentAccount.getSingleTradeCurrency();
            else if (!paymentAccount.getTradeCurrencies().isEmpty())
                tradeCurrency = paymentAccount.getTradeCurrencies().get(0);
        }

        checkNotNull(tradeCurrency, "tradeCurrency must not be null");
        tradeCurrencyCode.set(tradeCurrency.getCode());
    }

    void onCurrencySelected(TradeCurrency tradeCurrency) {
        if (tradeCurrency != null) {
            if (!this.tradeCurrency.equals(tradeCurrency)) {
                volume.set(null);
                minVolume.set(null);
                price.set(null);
                marketPriceMargin = 0;
            }

            this.tradeCurrency = tradeCurrency;
            final String code = this.tradeCurrency.getCode();
            tradeCurrencyCode.set(code);

            if (paymentAccount != null)
                paymentAccount.setSelectedTradeCurrency(tradeCurrency);

            priceFeedService.setCurrencyCode(code);

            Optional<TradeCurrency> tradeCurrencyOptional = preferences.getTradeCurrenciesAsObservable().stream().filter(e -> e.getCode().equals(code)).findAny();
            if (!tradeCurrencyOptional.isPresent()) {
                if (CurrencyUtil.isCryptoCurrency(code)) {
                    CurrencyUtil.getCryptoCurrency(code).ifPresent(preferences::addCryptoCurrency);
                } else {
                    CurrencyUtil.getFiatCurrency(code).ifPresent(preferences::addFiatCurrency);
                }
            }
        }
    }

    @Override
    public void onUpdateBalances(Coin availableConfirmedBalance,
                                 Coin availableNonBsqBalance,
                                 Coin unverifiedBalance,
                                 Coin unconfirmedChangeBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockedInBondsBalance,
                                 Coin unlockingBondsBalance) {
        updateBalance();
    }

    void fundFromSavingsWallet() {
        this.useSavingsWallet = true;
        updateBalance();
        if (!isXmrWalletFunded.get()) {
            this.useSavingsWallet = false;
            updateBalance();
        }
    }

    protected void setMarketPriceMargin(double marketPriceMargin) {
        this.marketPriceMargin = marketPriceMargin;
    }

    void requestTxFee(@Nullable Runnable actionHandler) {
        feeService.requestFees(() -> {
            txFeeFromXmrFeeService = feeService.getTxFee(feeTxSize);
            calculateTotalToPay();
            if (actionHandler != null)
                actionHandler.run();
        });
    }

    void setPreferredCurrencyForMakerFeeBtc(boolean preferredCurrencyForMakerFeeBtc) {
        preferences.setPayFeeInBtc(preferredCurrencyForMakerFeeBtc);
    }

    void setPreferredCurrencyForMakerFeeXmr(boolean preferredCurrencyForMakerFeeXmr) {
        preferences.setPayFeeInXmr(preferredCurrencyForMakerFeeXmr);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (minAmount.get() != null && amount.get() != null) {
            return !minAmount.get().isGreaterThan(amount.get());
        }
        return true;
    }

    OfferPayload.Direction getDirection() {
        return direction;
    }

    //TODO(niyid) Address for what?
    String getAddressEntry() {
        return bsqWalletService.getUnusedBsqAddressAsString();
    }

    protected TradeCurrency getTradeCurrency() {
        return tradeCurrency;
    }

    protected PaymentAccount getPaymentAccount() {
        return paymentAccount;
    }

    protected void setUseMarketBasedPrice(boolean useMarketBasedPrice) {
        this.useMarketBasedPrice.set(useMarketBasedPrice);
        preferences.setUsePercentageBasedPrice(useMarketBasedPrice);
    }

    public ObservableList<PaymentAccount> getPaymentAccounts() {
        return paymentAccounts;
    }

    public double getMarketPriceMargin() {
        return marketPriceMargin;
    }

    boolean isMakerFeeValid() {
        return preferences.isPayFeeInXmr() || isBsqForFeeAvailable();
    }

    long getMaxTradeLimit() {
        if (paymentAccount != null) {
            return accountAgeWitnessService.getMyTradeLimit(paymentAccount, tradeCurrencyCode.get(), direction);
        } else {
            return 0;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    double calculateMarketPriceManual(double marketPrice, double volumeAsDouble, double amountAsDouble) {
        double manualPriceAsDouble = volumeAsDouble / amountAsDouble;
        double percentage = MathUtils.roundDouble(manualPriceAsDouble / marketPrice, 4);

        setMarketPriceMargin(percentage);

        return manualPriceAsDouble;
    }

    void calculateVolume() {
        if (price.get() != null &&
                amount.get() != null &&
                !amount.get().isZero() &&
                !price.get().isZero()) {
            try {
                Volume volumeByAmount = calculateVolumeForAmount(amount);

                volume.set(volumeByAmount);

                calculateMinVolume();
            } catch (Throwable t) {
                log.error(t.toString());
            }
        }

        updateBalance();
    }

    void calculateMinVolume() {
        if (price.get() != null &&
                minAmount.get() != null &&
                !minAmount.get().isZero() &&
                !price.get().isZero()) {
            try {
                Volume volumeByAmount = calculateVolumeForAmount(minAmount);

                minVolume.set(volumeByAmount);

            } catch (Throwable t) {
                log.error(t.toString());
            }
        }
    }

    private Volume calculateVolumeForAmount(ObjectProperty<XmrCoin> xmrAmount) {
        Volume volumeByAmount = price.get().getVolumeByAmount(Coin.parseCoin(xmrAmount.get().toPlainString()));

        // For HalCash we want multiple of 10 EUR
        if (isHalCashAccount())
            volumeByAmount = XmrOfferUtil.getAdjustedVolumeForHalCash(volumeByAmount);
        else if (CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get()))
            volumeByAmount = XmrOfferUtil.getRoundedFiatVolume(volumeByAmount);
        
        return volumeByAmount;
    }

    void calculateAmount() {
        if (volume.get() != null &&
                price.get() != null &&
                !volume.get().isZero() &&
                !price.get().isZero() &&
                allowAmountUpdate) {
            try {
            	//TODO(niyid) This was the problem
//                XmrCoin value = DisplayUtils.reduceTo4Decimals(XmrCoin.fromCoin2XmrCoin(price.get().getAmountByVolume(volume.get()), String.valueOf(xmrMarketPrice.getPrice())), xmrFormatter);
            	btcToXmrRate = xmrMarketPrice.getPrice();
                XmrCoin value = DisplayUtils.reduceTo4Decimals(XmrCoin.fromCoinValue(price.get().getAmountByVolume(volume.get()).value), xmrFormatter);
                if (isHalCashAccount())
                    value = XmrOfferUtil.getAdjustedAmountForHalCash(value, price.get(), getMaxTradeLimit(), btcToXmrRate);
                else if (CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get()))
                    value = XmrOfferUtil.getRoundedFiatAmount(value, price.get(), getMaxTradeLimit(), btcToXmrRate);

                calculateVolume();

                amount.set(value);
                calculateTotalToPay();
            } catch (Throwable t) {
                log.error(t.toString());
            }
        }
    }

    void calculateTotalToPay() {
        // Maker does not pay the mining fee for the trade txs because the mining fee might be different when maker
        // created the offer and reserved his funds, so that would not work well with dynamic fees.
        // The mining fee for the createOfferFee tx is deducted from the createOfferFee and not visible to the trader
    	//TODO(niyid) feeAndSecDeposit = total trade fee including tx fee calculated from XmrWalletRpcWrapper.createTx ???
    	//TODO(niyid) if isCurrencyForMakerFeeXmr() then feeAndSecDeposit = (securityDeposit + makerFee) else feeAndSecDeposit = securityDeposit ???
        final XmrCoin makerFee = getMakerFee();
        if (direction != null && amount.get() != null && makerFee != null) {
            XmrCoin feeAndSecDeposit = getTxFee().add(getSecurityDeposit());
            if (isCurrencyForMakerFeeXmr())
                feeAndSecDeposit = feeAndSecDeposit.add(makerFee);
            XmrCoin total = isBuyOffer() ? feeAndSecDeposit : feeAndSecDeposit.add(amount.get());
            totalToPayAsCoin.set(total);
            updateBalance();
        }
    }

    XmrCoin getSecurityDeposit() {
        return isBuyOffer() ? getBuyerSecurityDepositAsCoin() : getSellerSecurityDepositAsCoin();
    }

    public boolean isBuyOffer() {
        return XmrOfferUtil.isBuyOffer(getDirection());
    }

    public XmrCoin getTxFee() {
        if (isCurrencyForMakerFeeXmr())
            return txFeeFromXmrFeeService;
        else
            return txFeeFromXmrFeeService.subtract(getMakerFee());
    }

    public void swapTradeToSavings() {
        btcWalletService.resetAddressEntriesForOpenOffer(offerId);
    }

    private void fillPaymentAccounts() {
        if (user.getPaymentAccounts() != null)
            paymentAccounts.setAll(new HashSet<>(user.getPaymentAccounts()));
    }

    protected void setAmount(XmrCoin amount) {
        this.amount.set(amount);
    }

    protected void setPrice(Price price) {
        this.price.set(price);
    }

    protected void setVolume(Volume volume) {
        this.volume.set(volume);
    }

    void setBuyerSecurityDeposit(double value) {
        this.buyerSecurityDeposit.set(value);
        preferences.setBuyerSecurityDepositAsPercent(value, getPaymentAccount());
    }

    protected boolean isUseMarketBasedPriceValue() {
        return marketPriceAvailable && useMarketBasedPrice.get() && !isHalCashAccount();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected ReadOnlyObjectProperty<XmrCoin> getAmount() {
        return amount;
    }

    protected ReadOnlyObjectProperty<XmrCoin> getMinAmount() {
        return minAmount;
    }

    public ReadOnlyObjectProperty<Price> getPrice() {
        return price;
    }

    ReadOnlyObjectProperty<Volume> getVolume() {
        return volume;
    }

    ReadOnlyObjectProperty<Volume> getMinVolume() {
        return minVolume;
    }

    protected void setMinAmount(XmrCoin minAmount) {
        this.minAmount.set(minAmount);
    }

    public ReadOnlyStringProperty getTradeCurrencyCode() {
        return tradeCurrencyCode;
    }

    ReadOnlyBooleanProperty getUseMarketBasedPrice() {
        return useMarketBasedPrice;
    }

    ReadOnlyDoubleProperty getBuyerSecurityDeposit() {
        return buyerSecurityDeposit;
    }

    protected XmrCoin getBuyerSecurityDepositAsCoin() {
        XmrCoin percentOfAmountAsCoin = XmrCoinUtil.getPercentOfAmountAsCoin(buyerSecurityDeposit.get(), amount.get());
        return getBoundedBuyerSecurityDepositAsCoin(percentOfAmountAsCoin);
    }

    XmrCoin getSellerSecurityDepositAsCoin() {
        XmrCoin amountAsCoin = this.amount.get();
        if (amountAsCoin == null)
            amountAsCoin = XmrCoin.ZERO;

        XmrCoin percentOfAmountAsCoin = XmrCoinUtil.getPercentOfAmountAsCoin(sellerSecurityDeposit.get(), amountAsCoin);
        return getBoundedSellerSecurityDepositAsCoin(percentOfAmountAsCoin);
    }

    private XmrCoin getBoundedBuyerSecurityDepositAsCoin(XmrCoin value) {
        // We need to ensure that for small amount values we don't get a too low BTC amount. We limit it with using the
        // MinBuyerSecurityDepositAsCoin from XmrRestrictions.
        return XmrCoin.valueOf(Math.max(XmrRestrictions.getMinBuyerSecurityDepositAsCoin(1.0 / xmrMarketPrice.getPrice()).value, value.value));
    }

    private XmrCoin getBoundedSellerSecurityDepositAsCoin(XmrCoin value) {
        // We need to ensure that for small amount values we don't get a too low BTC amount. We limit it with using the
        // MinSellerSecurityDepositAsCoin from XmrRestrictions.
        return XmrCoin.valueOf(Math.max(XmrRestrictions.getMinSellerSecurityDepositAsCoin(1.0 / xmrMarketPrice.getPrice()).value, value.value));
    }

    ReadOnlyObjectProperty<XmrCoin> totalToPayAsCoinProperty() {
        return totalToPayAsCoin;
    }

    public Coin getBsqBalance() {
        return bsqWalletService.getAvailableConfirmedBalance();
    }

    public void setMarketPriceAvailable(boolean marketPriceAvailable) {
        this.marketPriceAvailable = marketPriceAvailable;
    }

    public XmrCoin getMakerFee(boolean isCurrencyForMakerFeeXmr) {
        return XmrOfferUtil.getXmrMakerFee(isCurrencyForMakerFeeXmr, amount.get(), String.valueOf(xmrMarketPrice.getPrice()));
    }

    public XmrCoin getMakerFee() {
        return makerFeeProvider.getMakerFee(bsqWalletService, preferences, amount.get(), String.valueOf(xmrMarketPrice.getPrice()));
    }

    public XmrCoin getMakerFeeInXmr() {
        return XmrOfferUtil.getXmrMakerFee(true, amount.get(), String.valueOf(xmrMarketPrice.getPrice()));
    }

    public Coin getMakerFeeInBsq() {
        return OfferUtil.getMakerFee(false, XmrCoin.fromXmrCoin2Coin(amount.get(), "BSQ", String.valueOf(bsqToXmrRate)));
    }

    public boolean isCurrencyForMakerFeeXmr() {
        return XmrOfferUtil.isCurrencyForMakerFeeXmr(preferences, bsqWalletService, amount.get(), String.valueOf(xmrMarketPrice.getPrice()));
    }

    public boolean isPreferredFeeCurrencyXmr() {
        return preferences.isUseBisqXmrWallet() && preferences.isPayFeeInXmr();
    }

    public boolean isBsqForFeeAvailable() {
        return XmrOfferUtil.isBsqForMakerFeeAvailable(bsqWalletService, amount.get(), String.valueOf(xmrMarketPrice.getPrice()));
    }

    public boolean isHalCashAccount() {
        return paymentAccount instanceof HalCashAccount;
    }

    public boolean canPlaceOffer() {
        return GUIUtil.isBootstrappedOrShowPopup(p2PService) &&
                GUIUtil.canCreateOrTakeOfferOrShowPopup(user, navigation);
    }
}
