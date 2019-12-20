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
import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.main.MainView;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.deposit.DepositView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.settings.SettingsView;
import bisq.desktop.main.settings.preferences.PreferencesView;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.validation.AltcoinValidator;
import bisq.desktop.util.validation.BsqValidator;
import bisq.desktop.util.validation.Xmr2Validator;
import bisq.desktop.util.validation.FiatPriceValidator;
import bisq.desktop.util.validation.FiatVolumeValidator;
import bisq.desktop.util.validation.MonetaryValidator;
import bisq.desktop.util.validation.SecurityDepositValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.xmr.wallet.XmrRestrictions;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferRestrictions;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.XmrOfferUtil;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Trade;
import bisq.core.trade.Trade.TradeBaseCurrency;
import bisq.core.user.Preferences;
import bisq.core.util.XmrBSFormatter;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;
import bisq.core.util.CoinFormatter;
import bisq.core.util.ParsingUtils;
import bisq.core.util.validation.InputValidator;
import bisq.core.xmr.wallet.XmrWalletRpcWrapper;
import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.MathUtils;

import bisq.core.xmr.XmrCoin;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.util.Callback;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static javafx.beans.binding.Bindings.createStringBinding;

public abstract class XmrMutableOfferViewModel<M extends XmrMutableOfferDataModel> extends ActivatableWithDataModel<M> {
    private final Xmr2Validator xmrValidator;
    private final BsqValidator bsqValidator;
    protected final SecurityDepositValidator securityDepositValidator;
    private final PriceFeedService priceFeedService;
    private AccountAgeWitnessService accountAgeWitnessService;
    private final Navigation navigation;
    private final Preferences preferences;
    protected final XmrBSFormatter xmrFormatter;
    private final BsqFormatter bsqFormatter;
    private final FiatVolumeValidator fiatVolumeValidator;
    private final FiatPriceValidator fiatPriceValidator;
    private final AltcoinValidator altcoinValidator;
    final XmrWalletRpcWrapper xmrWalletWrapper;

    private String amountDescription;
    private String directionLabel;
    private String addressAsString;
    private final String paymentLabel;
    private boolean createOfferRequested;

    public final StringProperty amount = new SimpleStringProperty();
    public final StringProperty minAmount = new SimpleStringProperty();
    protected final StringProperty buyerSecurityDeposit = new SimpleStringProperty();
    final StringProperty buyerSecurityDepositInXMR = new SimpleStringProperty();
    final StringProperty buyerSecurityDepositLabel = new SimpleStringProperty();

    // Price in the viewModel is always dependent on fiat/altcoin: Fiat Fiat/BTC, for altcoins we use inverted price.
    // The domain (dataModel) uses always the same price model (otherCurrencyBTC)
    // If we would change the price representation in the domain we would not be backward compatible
    public final StringProperty price = new SimpleStringProperty();
    final StringProperty tradeFee = new SimpleStringProperty();
    final StringProperty tradeFeeInXmrWithFiat = new SimpleStringProperty();
    final StringProperty tradeFeeInBsqWithFiat = new SimpleStringProperty();
    final StringProperty tradeFeeCurrencyCode = new SimpleStringProperty();
    final StringProperty tradeFeeDescription = new SimpleStringProperty();
    final BooleanProperty isTradeFeeVisible = new SimpleBooleanProperty(false);

    // Positive % value means always a better price form the maker's perspective:
    // Buyer (with fiat): lower price as market
    // Buyer (with altcoin): higher (display) price as market (display price is inverted)
    public final StringProperty marketPriceMargin = new SimpleStringProperty();
    public final StringProperty volume = new SimpleStringProperty();
    final StringProperty volumeDescriptionLabel = new SimpleStringProperty();
    final StringProperty volumePromptLabel = new SimpleStringProperty();
    final StringProperty tradeAmount = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty errorMessage = new SimpleStringProperty();
    final StringProperty tradeCurrencyCode = new SimpleStringProperty();
    final StringProperty waitingForFundsText = new SimpleStringProperty("");

    final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty cancelButtonDisabled = new SimpleBooleanProperty();
    public final BooleanProperty isNextButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty placeOfferCompleted = new SimpleBooleanProperty();
    final BooleanProperty showPayFundsScreenDisplayed = new SimpleBooleanProperty();
    private final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty isWaitingForFunds = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> minAmountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> priceValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> volumeValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> buyerSecurityDepositValidationResult = new SimpleObjectProperty<>();

    // Those are needed for the XmrAddressTextField
    private final ObjectProperty<String> address = new SimpleObjectProperty<>();

    private ChangeListener<String> amountStringListener;
    private ChangeListener<String> minAmountStringListener;
    private ChangeListener<String> priceStringListener, marketPriceMarginStringListener;
    private ChangeListener<String> volumeStringListener;
    private ChangeListener<String> securityDepositStringListener;

    private ChangeListener<XmrCoin> amountAsCoinListener;
    private ChangeListener<XmrCoin> minAmountAsCoinListener;
    private ChangeListener<Price> priceListener;
    private ChangeListener<Volume> volumeListener;
    private ChangeListener<Number> securityDepositAsDoubleListener;

    private ChangeListener<Boolean> isWalletFundedListener;
    //private ChangeListener<XmrCoin> feeFromFundingTxListener;
    private ChangeListener<String> errorMessageListener;
    private Offer offer;
    private Timer timeoutTimer;
    private boolean inputIsMarketBasedPrice;
    private ChangeListener<Boolean> useMarketBasedPriceListener;
    private boolean ignorePriceStringListener, ignoreVolumeStringListener, ignoreAmountStringListener, ignoreSecurityDepositStringListener;
    private MarketPrice marketPrice;
    final IntegerProperty marketPriceAvailableProperty = new SimpleIntegerProperty(-1);
    private ChangeListener<Number> currenciesUpdateListener;
    protected boolean syncMinAmountWithAmount = true;
    protected MarketPrice xmrMarketPrice;
    protected MarketPrice bsqMarketPrice;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public XmrMutableOfferViewModel(M dataModel,
                                 FiatVolumeValidator fiatVolumeValidator,
                                 FiatPriceValidator fiatPriceValidator,
                                 AltcoinValidator altcoinValidator,
                                 Xmr2Validator xmrValidator,
                                 BsqValidator bsqValidator,
                                 SecurityDepositValidator securityDepositValidator,
                                 PriceFeedService priceFeedService,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 Navigation navigation,
                                 Preferences preferences,
                                 XmrBSFormatter xmrFormatter,
                                 BsqFormatter bsqFormatter,
                                 XmrWalletRpcWrapper xmrWalletWrapper) {
        super(dataModel);

        this.fiatVolumeValidator = fiatVolumeValidator;
        this.fiatPriceValidator = fiatPriceValidator;
        this.altcoinValidator = altcoinValidator;
        this.xmrValidator = xmrValidator;
        this.bsqValidator = bsqValidator;
        this.securityDepositValidator = securityDepositValidator;
        this.priceFeedService = priceFeedService;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.navigation = navigation;
        this.preferences = preferences;
        this.xmrFormatter = xmrFormatter;
        this.bsqFormatter = bsqFormatter;
        this.xmrWalletWrapper = xmrWalletWrapper;

        paymentLabel = Res.get("createOffer.fundsBox.paymentLabel", dataModel.shortOfferId);

        xmrMarketPrice = priceFeedService.getMarketPrice("XMR");
        bsqMarketPrice = priceFeedService.getMarketPrice("BSQ");

        if (dataModel.getAddressEntry() != null) {
            addressAsString = dataModel.getAddressEntry();
            address.set(dataModel.getAddressEntry());
        }
        createListeners();
    }

    @Override
    public void activate() {
        if (DevEnv.isDevMode()) {
            UserThread.runAfter(() -> {
                amount.set("0.001"); //TODO(niyid) Hmm.
                price.set("75000"); // for CNY
                minAmount.set(amount.get());
                onFocusOutPriceAsPercentageTextField(true, false);
                applyMakerFee();
                setAmountToModel();
                setMinAmountToModel();
                setPriceToModel();
                dataModel.calculateVolume();
                dataModel.calculateTotalToPay();
                updateButtonDisableState();
                updateSpinnerInfo();
            }, 100, TimeUnit.MILLISECONDS);
        }

        addBindings();
        addListeners();

        updateButtonDisableState();

        updateMarketPriceAvailable();
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeListeners();
        stopTimeoutTimer();
    }

    private void addBindings() {
        if (dataModel.getDirection() == OfferPayload.Direction.BUY) {
            volumeDescriptionLabel.bind(createStringBinding(
                    () -> Res.get("createOffer.amountPriceBox.buy.volumeDescription", dataModel.getTradeCurrencyCode().get()),
                    dataModel.getTradeCurrencyCode()));
        } else {
            volumeDescriptionLabel.bind(createStringBinding(
                    () -> Res.get("createOffer.amountPriceBox.sell.volumeDescription", dataModel.getTradeCurrencyCode().get()),
                    dataModel.getTradeCurrencyCode()));
        }
        volumePromptLabel.bind(createStringBinding(
                () -> Res.get("createOffer.volume.prompt", dataModel.getTradeCurrencyCode().get()),
                dataModel.getTradeCurrencyCode()));

        totalToPay.bind(createStringBinding(() -> xmrFormatter.formatCoinWithCode(dataModel.totalToPayAsCoinProperty().get()),
                dataModel.totalToPayAsCoinProperty()));


        tradeAmount.bind(createStringBinding(() -> xmrFormatter.formatCoinWithCode(dataModel.getAmount().get()),
                dataModel.getAmount()));


        tradeCurrencyCode.bind(dataModel.getTradeCurrencyCode());
    }

    private void removeBindings() {
        totalToPay.unbind();
        tradeAmount.unbind();
        tradeCurrencyCode.unbind();
        volumeDescriptionLabel.unbind();
        volumePromptLabel.unbind();
    }

    private void createListeners() {
        amountStringListener = (ov, oldValue, newValue) -> {
            if (!ignoreAmountStringListener) {
                if (isXmrInputValid(newValue).isValid) {
                    setAmountToModel();
                    dataModel.calculateVolume();
                    dataModel.calculateTotalToPay();
                }
                updateButtonDisableState();
            }
        };
        minAmountStringListener = (ov, oldValue, newValue) -> {
            if (isXmrInputValid(newValue).isValid)
                setMinAmountToModel();
            updateButtonDisableState();
        };
        priceStringListener = (ov, oldValue, newValue) -> {
            updateMarketPriceAvailable();
            final String currencyCode = dataModel.getTradeCurrencyCode().get();
            if (!ignorePriceStringListener) {
                if (isPriceInputValid(newValue).isValid) {
                    setPriceToModel();
                    dataModel.calculateVolume();
                    dataModel.calculateTotalToPay();

                    if (!inputIsMarketBasedPrice) {
                        if (marketPrice != null && marketPrice.isRecentExternalPriceAvailable()) {
                            double marketPriceAsDouble = marketPrice.getPrice();
                            try {
                                double priceAsDouble = ParsingUtils.parseNumberStringToDouble(price.get());
                                double relation = priceAsDouble / marketPriceAsDouble;
                                final OfferPayload.Direction compareDirection = CurrencyUtil.isCryptoCurrency(currencyCode) ?
                                        OfferPayload.Direction.SELL :
                                        OfferPayload.Direction.BUY;
                                double percentage = dataModel.getDirection() == compareDirection ? 1 - relation : relation - 1;
                                percentage = MathUtils.roundDouble(percentage, 4);
                                dataModel.setMarketPriceMargin(percentage);
                                marketPriceMargin.set(XmrBSFormatter.formatToPercent(percentage));
                                applyMakerFee();
                            } catch (NumberFormatException t) {
                                marketPriceMargin.set("");
                                new Popup<>().warning(Res.get("validation.NaN")).show();
                            }
                        } else {
                            log.debug("We don't have a market price. We use the static price instead.");
                        }
                    }
                }
            }
            updateButtonDisableState();
        };
        marketPriceMarginStringListener = (ov, oldValue, newValue) -> {
            if (inputIsMarketBasedPrice) {
                try {
                    if (!newValue.isEmpty() && !newValue.equals("-")) {
                        double percentage = ParsingUtils.parsePercentStringToDouble(newValue);
                        if (percentage >= 1 || percentage <= -1) {
                            new Popup<>().warning(Res.get("popup.warning.tooLargePercentageValue") + "\n" +
                                    Res.get("popup.warning.examplePercentageValue"))
                                    .show();
                        } else {
                            final String currencyCode = dataModel.getTradeCurrencyCode().get();
                            MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
                            if (marketPrice != null && marketPrice.isRecentExternalPriceAvailable()) {
                                percentage = MathUtils.roundDouble(percentage, 4);
                                double marketPriceAsDouble = marketPrice.getPrice();
                                final boolean isCryptoCurrency = CurrencyUtil.isCryptoCurrency(currencyCode);
                                final OfferPayload.Direction compareDirection = isCryptoCurrency ?
                                        OfferPayload.Direction.SELL :
                                        OfferPayload.Direction.BUY;
                                double factor = dataModel.getDirection() == compareDirection ?
                                        1 - percentage :
                                        1 + percentage;
                                double targetPrice = marketPriceAsDouble * factor;
                                int precision = isCryptoCurrency ?
                                        Altcoin.SMALLEST_UNIT_EXPONENT : Fiat.SMALLEST_UNIT_EXPONENT;
                                // protect from triggering unwanted updates
                                ignorePriceStringListener = true;
                                price.set(XmrBSFormatter.formatRoundedDoubleWithPrecision(targetPrice, precision));
                                ignorePriceStringListener = false;
                                setPriceToModel();
                                dataModel.setMarketPriceMargin(percentage);
                                dataModel.calculateVolume();
                                dataModel.calculateTotalToPay();
                                updateButtonDisableState();
                                applyMakerFee();
                            } else {
                                marketPriceMargin.set("");
                                String id = "showNoPriceFeedAvailablePopup";
                                if (preferences.showAgain(id)) {
                                    new Popup<>().warning(Res.get("popup.warning.noPriceFeedAvailable"))
                                            .dontShowAgainId(id)
                                            .show();
                                }
                            }
                        }
                    }
                } catch (NumberFormatException t) {
                    log.error(t.toString());
                    t.printStackTrace();
                    new Popup<>().warning(Res.get("validation.NaN")).show();
                } catch (Throwable t) {
                    log.error(t.toString());
                    t.printStackTrace();
                    new Popup<>().warning(Res.get("validation.inputError", t.toString())).show();
                }
            }
        };
        useMarketBasedPriceListener = (observable, oldValue, newValue) -> {
            if (newValue)
                priceValidationResult.set(new InputValidator.ValidationResult(true));
        };

        volumeStringListener = (ov, oldValue, newValue) -> {
            if (!ignoreVolumeStringListener) {
                if (isVolumeInputValid(newValue).isValid) {
                    setVolumeToModel();
                    setPriceToModel();
                    dataModel.calculateAmount();
                    dataModel.calculateTotalToPay();
                }
                updateButtonDisableState();
            }
        };
        securityDepositStringListener = (ov, oldValue, newValue) -> {
            if (!ignoreSecurityDepositStringListener) {
                if (securityDepositValidator.validate(newValue).isValid) {
                    setBuyerSecurityDepositToModel();
                    dataModel.calculateTotalToPay();
                }
                updateButtonDisableState();
            }
        };


        amountAsCoinListener = (ov, oldValue, newValue) -> {
            if (newValue != null) {
                amount.set(xmrFormatter.formatCoin(newValue));
                buyerSecurityDepositInXMR.set(xmrFormatter.formatCoinWithCode(dataModel.getBuyerSecurityDepositAsCoin()));
            } else {
                amount.set("");
                buyerSecurityDepositInXMR.set("");
            }

            applyMakerFee();
        };
        minAmountAsCoinListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                minAmount.set(xmrFormatter.formatCoin(newValue));
            else
                minAmount.set("");
        };
        priceListener = (ov, oldValue, newValue) -> {
            ignorePriceStringListener = true;
            if (newValue != null)
                price.set(XmrBSFormatter.formatPrice(newValue));
            else
                price.set("");

            ignorePriceStringListener = false;
            applyMakerFee();
        };
        volumeListener = (ov, oldValue, newValue) -> {
            ignoreVolumeStringListener = true;
            if (newValue != null)
                volume.set(DisplayUtils.formatVolume(newValue));
            else
                volume.set("");

            ignoreVolumeStringListener = false;
            applyMakerFee();
        };

        securityDepositAsDoubleListener = (ov, oldValue, newValue) -> {
            if (newValue != null) {
                buyerSecurityDeposit.set(XmrBSFormatter.formatToPercent((double) newValue));
                if (dataModel.getAmount().get() != null)
                    buyerSecurityDepositInXMR.set(xmrFormatter.formatCoinWithCode(dataModel.getBuyerSecurityDepositAsCoin()));
            } else {
                buyerSecurityDeposit.set("");
                buyerSecurityDepositInXMR.set("");
            }
        };


        isWalletFundedListener = (ov, oldValue, newValue) -> updateButtonDisableState();
       /* feeFromFundingTxListener = (ov, oldValue, newValue) -> {
            updateButtonDisableState();
        };*/

        currenciesUpdateListener = (observable, oldValue, newValue) -> {
            updateMarketPriceAvailable();
            updateButtonDisableState();
        };
    }

    private void applyMakerFee() {
    	//TODO(niyid) Fee should be in XMR and not BTC here.
        Coin makerFeeAsCoin = dataModel.getMakerFeeInBsq();
        if (makerFeeAsCoin != null) {
            isTradeFeeVisible.setValue(true);

            tradeFee.set(getFormatterForMakerFee(makerFeeAsCoin).formatCoin(makerFeeAsCoin));

            Coin makerFeeInBsq = dataModel.getMakerFeeInBsq();
            XmrCoin makerFeeInXmr = dataModel.getMakerFee();
            Optional<Volume> optionalFeeInFiat = OfferUtil.getFeeInUserFiatCurrency(makerFeeInBsq,
                    false, preferences, priceFeedService, bsqFormatter);
            String xmrFeeWithFiatAmount = DisplayUtils.getFeeWithFiatAmount(makerFeeInBsq, optionalFeeInFiat, bsqFormatter);
            if (DevEnv.isDaoActivated()) {
                tradeFeeInXmrWithFiat.set(xmrFeeWithFiatAmount);
            } else {
                tradeFeeInXmrWithFiat.set(xmrFormatter.formatCoinWithCode(makerFeeInXmr));
            }

          //TODO(niyid) XmrOfferUtil.getFeeInUserFiatCurrency
            Optional<Volume> optionalBsqFeeInFiat = OfferUtil.getFeeInUserFiatCurrency(makerFeeInBsq,
                    false, preferences, priceFeedService, bsqFormatter);
            String bsqFeeWithFiatAmount = DisplayUtils.getFeeWithFiatAmount(makerFeeInBsq, optionalBsqFeeInFiat, bsqFormatter);
            if (DevEnv.isDaoActivated()) {
                tradeFeeInBsqWithFiat.set(bsqFeeWithFiatAmount);
            } else {
                // Before DAO is enabled we show fee as fiat and % in second line
                String feeInFiatAsString;
                if (optionalFeeInFiat != null && optionalFeeInFiat.isPresent()) {
                    feeInFiatAsString = DisplayUtils.formatVolumeWithCode(optionalFeeInFiat.get());
                } else {
                    feeInFiatAsString = Res.get("shared.na");
                }

                double amountAsLong = (double) dataModel.getAmount().get().value;
                Coin makerFeeInBtc = dataModel.getMakerFeeInBtc();
                double makerFeeInBtcAsLong = (double) makerFeeInBtc.value;
                double percent = makerFeeInBtcAsLong / amountAsLong;

                tradeFeeInBsqWithFiat.set(Res.get("createOffer.tradeFee.fiatAndPercent",
                        feeInFiatAsString,
                        XmrBSFormatter.formatToPercentWithSymbol(percent)));
            }
        }
        tradeFeeCurrencyCode.set(dataModel.isCurrencyForMakerFeeXmr() ? Trade.TradeBaseCurrency.XMR.name() : "BSQ");
        tradeFeeDescription.set(DevEnv.isDaoActivated() ? Res.get("createOffer.tradeFee.descriptionBSQEnabled") :
                Res.get("createOffer.tradeFee.descriptionBTCOnly"));
    }

    private void updateMarketPriceAvailable() {
        marketPrice = priceFeedService.getMarketPrice(dataModel.getTradeCurrencyCode().get());
        marketPriceAvailableProperty.set(marketPrice == null || !marketPrice.isExternallyProvidedPrice() ? 0 : 1);
        dataModel.setMarketPriceAvailable(marketPrice != null && marketPrice.isExternallyProvidedPrice());

        xmrMarketPrice = priceFeedService.getMarketPrice("XMR");
        bsqMarketPrice = priceFeedService.getMarketPrice("BSQ");
    }

    private void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountStringListener);
        minAmount.addListener(minAmountStringListener);
        price.addListener(priceStringListener);
        marketPriceMargin.addListener(marketPriceMarginStringListener);
        dataModel.getUseMarketBasedPrice().addListener(useMarketBasedPriceListener);
        volume.addListener(volumeStringListener);
        buyerSecurityDeposit.addListener(securityDepositStringListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getAmount().addListener(amountAsCoinListener);
        dataModel.getMinAmount().addListener(minAmountAsCoinListener);
        dataModel.getPrice().addListener(priceListener);
        dataModel.getVolume().addListener(volumeListener);
        dataModel.getBuyerSecurityDeposit().addListener(securityDepositAsDoubleListener);

        // dataModel.feeFromFundingTxProperty.addListener(feeFromFundingTxListener);
        dataModel.getIsXmrWalletFunded().addListener(isWalletFundedListener);

        priceFeedService.updateCounterProperty().addListener(currenciesUpdateListener);
    }

    private void removeListeners() {
        amount.removeListener(amountStringListener);
        minAmount.removeListener(minAmountStringListener);
        price.removeListener(priceStringListener);
        marketPriceMargin.removeListener(marketPriceMarginStringListener);
        dataModel.getUseMarketBasedPrice().removeListener(useMarketBasedPriceListener);
        volume.removeListener(volumeStringListener);
        buyerSecurityDeposit.removeListener(securityDepositStringListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getAmount().removeListener(amountAsCoinListener);
        dataModel.getMinAmount().removeListener(minAmountAsCoinListener);
        dataModel.getPrice().removeListener(priceListener);
        dataModel.getVolume().removeListener(volumeListener);
        dataModel.getBuyerSecurityDeposit().removeListener(securityDepositAsDoubleListener);

        //dataModel.feeFromFundingTxProperty.removeListener(feeFromFundingTxListener);
        dataModel.getIsXmrWalletFunded().removeListener(isWalletFundedListener);

        if (offer != null && errorMessageListener != null)
            offer.getErrorMessageProperty().removeListener(errorMessageListener);

        priceFeedService.updateCounterProperty().removeListener(currenciesUpdateListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean initWithData(OfferPayload.Direction direction, TradeCurrency tradeCurrency) {
        boolean result = dataModel.initWithData(direction, tradeCurrency);
        //TODO(niyid) PaymentAccount for Monero should natively support XmrCoin
        double btcToXmrRate = xmrMarketPrice.getPrice(); 
        if (dataModel.paymentAccount != null) {
        	XmrCoin coinMaxValue = dataModel.paymentAccount.getPaymentMethod().getXmrMaxTradeLimitAsCoin(dataModel.getTradeCurrencyCode().get(), btcToXmrRate);
        	log.info("Validator Max Value => {}", coinMaxValue.toFriendlyString());
            xmrValidator.setMaxValue(coinMaxValue);        	
        }
      //TODO(niyid) For now convert maxTradeLimit from BTC to XMR
        XmrCoin maxTradeAmount = XmrCoin.fromCoin2XmrCoin(Coin.valueOf(dataModel.getMaxTradeLimit()), String.valueOf(btcToXmrRate));
        log.info("Max Trade Amount => {}", maxTradeAmount.toFriendlyString());
        xmrValidator.setMaxTradeLimit(maxTradeAmount);
        XmrCoin minTradeAmount = XmrRestrictions.getMinTradeAmount(btcToXmrRate);
        log.info("Min Trade Amount => {}", minTradeAmount.toFriendlyString());
        //XmrRestrictions.getMinTradeAmount()
        xmrValidator.setMinValue(minTradeAmount);

        final boolean isBuy = dataModel.getDirection() == OfferPayload.Direction.BUY;
        directionLabel = isBuy ? Res.get("shared.buyXxx", Trade.TradeBaseCurrency.XMR) : Res.get("shared.sellXxx", Trade.TradeBaseCurrency.XMR);
        amountDescription = Res.get("createOffer.amountPriceBox.amountDescription", Trade.TradeBaseCurrency.XMR,
                isBuy ? Res.get("shared.buy") : Res.get("shared.sell"));

        securityDepositValidator.setPaymentAccount(dataModel.paymentAccount);
        buyerSecurityDeposit.set(XmrBSFormatter.formatToPercent(dataModel.getBuyerSecurityDeposit().get()));
        buyerSecurityDepositLabel.set(getSecurityDepositLabel());

        applyMakerFee();
        return result;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onPlaceOffer(Offer offer, Runnable resultHandler) {
        errorMessage.set(null);
        createOfferRequested = true;

        if (timeoutTimer == null) {
            timeoutTimer = UserThread.runAfter(() -> {
                stopTimeoutTimer();
                createOfferRequested = false;
                errorMessage.set(Res.get("createOffer.timeoutAtPublishing"));

                updateButtonDisableState();
                updateSpinnerInfo();

                resultHandler.run();
            }, 60);
        }
        errorMessageListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                stopTimeoutTimer();
                createOfferRequested = false;
                if (offer.getState() == Offer.State.OFFER_FEE_PAID)
                    errorMessage.set(newValue + Res.get("createOffer.errorInfo"));
                else
                    errorMessage.set(newValue);

                updateButtonDisableState();
                updateSpinnerInfo();

                resultHandler.run();
            }
        };

        offer.errorMessageProperty().addListener(errorMessageListener);

        dataModel.onPlaceOffer(offer, transaction -> {
            stopTimeoutTimer();
            resultHandler.run();
            placeOfferCompleted.set(true);
            errorMessage.set(null);
        });

        updateButtonDisableState();
        updateSpinnerInfo();
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        dataModel.onPaymentAccountSelected(paymentAccount);
        if (amount.get() != null)
            amountValidationResult.set(isXmrInputValid(amount.get()));
        
        double btcToXmrRate = xmrMarketPrice.getPrice(); 

        xmrValidator.setMaxValue(dataModel.paymentAccount.getPaymentMethod().getXmrMaxTradeLimitAsCoin(dataModel.getTradeCurrencyCode().get(), btcToXmrRate));

        XmrCoin maxTradeAmount = XmrCoin.fromCoin2XmrCoin(Coin.valueOf(dataModel.getMaxTradeLimit()), String.valueOf(btcToXmrRate));

        xmrValidator.setMaxTradeLimit(maxTradeAmount);
        
        XmrCoin minTradeAmount = XmrRestrictions.getMinTradeAmount(btcToXmrRate);
        
        xmrValidator.setMinValue(minTradeAmount);
        securityDepositValidator.setPaymentAccount(paymentAccount);
    }

    public void onCurrencySelected(TradeCurrency tradeCurrency) {
        dataModel.onCurrencySelected(tradeCurrency);

        marketPrice = priceFeedService.getMarketPrice(dataModel.getTradeCurrencyCode().get());
        marketPriceAvailableProperty.set(marketPrice == null || !marketPrice.isExternallyProvidedPrice() ? 0 : 1);
        updateButtonDisableState();
    }

    void onShowPayFundsScreen(Runnable actionHandler) {
        dataModel.estimateTxSize();
        dataModel.requestTxFee(actionHandler);
        showPayFundsScreenDisplayed.set(true);
        updateSpinnerInfo();
    }

    boolean fundFromSavingsWallet() {
        dataModel.fundFromSavingsWallet();
        if (dataModel.getIsXmrWalletFunded().get()) {
            updateButtonDisableState();
            return true;
        } else {
            new Popup<>().warning(Res.get("shared.notEnoughFunds",
                    xmrFormatter.formatCoinWithCode(dataModel.totalToPayAsCoinProperty().get()),
                    xmrFormatter.formatCoinWithCode(dataModel.getTotalAvailableBalance())))
                    .actionButtonTextWithGoTo("navigation.funds.depositFunds")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
                    .show();
            return false;
        }

    }

    public void setIsCurrencyForMakerFeeBtc(boolean isCurrencyForMakerFeeBtc) {
        dataModel.setPreferredCurrencyForMakerFeeBtc(isCurrencyForMakerFeeBtc);
        applyMakerFee();
    }

    public void setIsCurrencyForMakerFeeXmr(boolean isCurrencyForMakerFeeXmr) {
        dataModel.setPreferredCurrencyForMakerFeeXmr(isCurrencyForMakerFeeXmr);
        applyMakerFee();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handle focus
    ///////////////////////////////////////////////////////////////////////////////////////////

    // On focus out we do validation and apply the data to the model
    void onFocusOutAmountTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
        	double btcToXmrRate = xmrMarketPrice.getPrice();
        	XmrCoin toleratedSmallTradeAmount = XmrCoin.fromCoin2XmrCoin(OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT, String.valueOf(btcToXmrRate));
            InputValidator.ValidationResult result = isXmrInputValid(amount.get());
            amountValidationResult.set(result);
            if (result.isValid) {
                setAmountToModel();
                ignoreAmountStringListener = true;
                amount.set(xmrFormatter.formatCoin(dataModel.getAmount().get()));
                ignoreAmountStringListener = false;
                dataModel.calculateVolume();

                if (!dataModel.isMinAmountLessOrEqualAmount())
                    minAmount.set(amount.get());
                else
                    amountValidationResult.set(result);

                if (minAmount.get() != null)
                    minAmountValidationResult.set(isXmrInputValid(minAmount.get()));
            } else if (amount.get() != null && xmrValidator.getMaxTradeLimit() != null && xmrValidator.getMaxTradeLimit().value == toleratedSmallTradeAmount.value) {
                amount.set(xmrFormatter.formatCoin(xmrValidator.getMaxTradeLimit()));
                new Popup<>().information(Res.get("popup.warning.tradeLimitDueAccountAgeRestriction.buyer",
                        xmrFormatter.formatCoinWithCode(toleratedSmallTradeAmount),
                        Res.get("offerbook.warning.newVersionAnnouncement")))
                        .width(900)
                        .show();
            }
            // We want to trigger a recalculation of the volume
            UserThread.execute(() -> {
                onFocusOutVolumeTextField(true, false);
                onFocusOutMinAmountTextField(true, false);
            });
        }
    }

    public void onFocusOutMinAmountTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isXmrInputValid(minAmount.get());
            minAmountValidationResult.set(result);
            if (result.isValid) {
                XmrCoin minAmountAsCoin = dataModel.getMinAmount().get();
                syncMinAmountWithAmount = minAmountAsCoin != null && minAmountAsCoin.equals(dataModel.getAmount().get());
                setMinAmountToModel();

                dataModel.calculateMinVolume();

                if (dataModel.getMinVolume().get() != null) {
                    InputValidator.ValidationResult minVolumeResult = isVolumeInputValid(
                            DisplayUtils.formatVolume(dataModel.getMinVolume().get()));

                    volumeValidationResult.set(minVolumeResult);

                    updateButtonDisableState();
                }

                this.minAmount.set(xmrFormatter.formatCoin(minAmountAsCoin));

                if (!dataModel.isMinAmountLessOrEqualAmount()) {
                    this.amount.set(this.minAmount.get());
                } else {
                    minAmountValidationResult.set(result);
                    if (this.amount.get() != null)
                        amountValidationResult.set(isXmrInputValid(this.amount.get()));
                }
            } else {
                syncMinAmountWithAmount = true;
            }
        }
    }

    void onFocusOutPriceTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isPriceInputValid(price.get());
            boolean isValid = result.isValid;
            priceValidationResult.set(result);
            if (isValid) {
                setPriceToModel();
                ignorePriceStringListener = true;
                if (dataModel.getPrice().get() != null)
                    price.set(XmrBSFormatter.formatPrice(dataModel.getPrice().get()));
                ignorePriceStringListener = false;
                dataModel.calculateVolume();
                dataModel.calculateAmount();
                applyMakerFee();
            }

            // We want to trigger a recalculation of the volume and minAmount
            UserThread.execute(() -> {
                onFocusOutVolumeTextField(true, false);
                // We also need to update minAmount
                onFocusOutAmountTextField(true, false);
                onFocusOutMinAmountTextField(true, false);
            });
        }
    }

    public void onFocusOutPriceAsPercentageTextField(boolean oldValue, boolean newValue) {
        inputIsMarketBasedPrice = !oldValue && newValue;
        if (oldValue && !newValue)
            if (marketPriceMargin.get() == null) {
                // field wasn't set manually
                inputIsMarketBasedPrice = true;
            }
        marketPriceMargin.set(XmrBSFormatter.formatRoundedDoubleWithPrecision(dataModel.getMarketPriceMargin() * 100, 2));

        // We want to trigger a recalculation of the volume
        UserThread.execute(() -> {
            onFocusOutVolumeTextField(true, false);
        });
    }

    void onFocusOutVolumeTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isVolumeInputValid(volume.get());
            volumeValidationResult.set(result);
            if (result.isValid) {
                setVolumeToModel();
                ignoreVolumeStringListener = true;

                Volume volume = dataModel.getVolume().get();
                if (volume != null) {
                    // For HalCash we want multiple of 10 EUR
                    if (dataModel.isHalCashAccount())
                        volume = XmrOfferUtil.getAdjustedVolumeForHalCash(volume);
                    else if (CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get()))
                        volume = XmrOfferUtil.getRoundedFiatVolume(volume);

                    this.volume.set(DisplayUtils.formatVolume(volume));
                }

                ignoreVolumeStringListener = false;

                dataModel.calculateAmount();

                if (!dataModel.isMinAmountLessOrEqualAmount()) {
                    minAmount.set(amount.getValue());
                } else {
                    if (amount.get() != null)
                        amountValidationResult.set(isXmrInputValid(amount.get()));

                    // We only check minAmountValidationResult if amountValidationResult is valid, otherwise we would get
                    // triggered a close of the popup when the minAmountValidationResult is applied
                    if (amountValidationResult.getValue() != null && amountValidationResult.getValue().isValid && minAmount.get() != null)
                        minAmountValidationResult.set(isXmrInputValid(minAmount.get()));
                }
            }
        }
    }

    void onFocusOutBuyerSecurityDepositTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = securityDepositValidator.validate(buyerSecurityDeposit.get());
            buyerSecurityDepositValidationResult.set(result);
            if (result.isValid) {
                double defaultSecurityDeposit = XmrRestrictions.getDefaultBuyerSecurityDepositAsPercent(getPaymentAccount());
                String key = "buyerSecurityDepositIsLowerAsDefault";
                double depositAsDouble = ParsingUtils.parsePercentStringToDouble(buyerSecurityDeposit.get());
                if (preferences.showAgain(key) && depositAsDouble < defaultSecurityDeposit) {
                    String postfix = dataModel.isBuyOffer() ?
                            Res.get("createOffer.tooLowSecDeposit.makerIsBuyer") :
                            Res.get("createOffer.tooLowSecDeposit.makerIsSeller");
                    new Popup<>()
                            .warning(Res.get("createOffer.tooLowSecDeposit.warning",
                                    XmrBSFormatter.formatToPercentWithSymbol(defaultSecurityDeposit)) + "\n\n" + postfix)
                            .width(800)
                            .actionButtonText(Res.get("createOffer.resetToDefault"))
                            .onAction(() -> {
                                dataModel.setBuyerSecurityDeposit(defaultSecurityDeposit);
                                ignoreSecurityDepositStringListener = true;
                                buyerSecurityDeposit.set(XmrBSFormatter.formatToPercent(dataModel.getBuyerSecurityDeposit().get()));
                                ignoreSecurityDepositStringListener = false;
                            })
                            .closeButtonText(Res.get("createOffer.useLowerValue"))
                            .onClose(this::applyBuyerSecurityDepositOnFocusOut)
                            .dontShowAgainId(key)
                            .show();
                } else {
                    applyBuyerSecurityDepositOnFocusOut();
                }
            }
        }
    }

    private void applyBuyerSecurityDepositOnFocusOut() {
        setBuyerSecurityDepositToModel();
        ignoreSecurityDepositStringListener = true;
        buyerSecurityDeposit.set(XmrBSFormatter.formatToPercent(dataModel.getBuyerSecurityDeposit().get()));
        ignoreSecurityDepositStringListener = false;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isPriceInRange() {
        if (marketPriceMargin.get() != null && !marketPriceMargin.get().isEmpty()) {
            if (Math.abs(ParsingUtils.parsePercentStringToDouble(marketPriceMargin.get())) > preferences.getMaxPriceDistanceInPercent()) {
                displayPriceOutOfRangePopup();
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    private void displayPriceOutOfRangePopup() {
        Popup popup = new Popup<>();
        popup.warning(Res.get("createOffer.priceOutSideOfDeviation",
                XmrBSFormatter.formatToPercentWithSymbol(preferences.getMaxPriceDistanceInPercent())))
                .actionButtonText(Res.get("createOffer.changePrice"))
                .onAction(popup::hide)
                .closeButtonTextWithGoTo("navigation.settings.preferences")
                .onClose(() -> navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class))
                .show();
    }

    XmrBSFormatter getBtcFormatter() {
        return xmrFormatter;
    }

    public boolean isSellOffer() {
        return dataModel.getDirection() == OfferPayload.Direction.SELL;
    }

    public TradeCurrency getTradeCurrency() {
        return dataModel.getTradeCurrency();
    }

    public String getBsqTradeAmount() {
        double bsqToXmrRate = xmrMarketPrice.getPrice() / bsqMarketPrice.getPrice();
        return bsqFormatter.formatBSQSatoshisWithCode(XmrCoin.fromXmrCoin2Coin(dataModel.getAmount().get(), "BSQ", String.valueOf(bsqToXmrRate)).value);
    }

    public String getTradeAmount() {
        return xmrFormatter.formatCoinWithCode(dataModel.getAmount().get());
    }

    public String getSecurityDepositLabel() {
        return dataModel.isBuyOffer() ? Res.get("createOffer.setDepositAsBuyer") : Res.get("createOffer.setDeposit");
    }

    public String getSecurityDepositPopOverLabel(String depositInBTC) {
        return dataModel.isBuyOffer() ? Res.get("createOffer.securityDepositInfoAsBuyer", depositInBTC) :
                Res.get("createOffer.securityDepositInfo", depositInBTC);
    }

    public String getSecurityDepositInfo() {
        return xmrFormatter.formatCoinWithCode(dataModel.getSecurityDeposit()) +
                GUIUtil.getPercentageOfTradeAmount(dataModel.getSecurityDeposit(), dataModel.getAmount().get());
    }

    public String getBsqSecurityDepositInfo() {
    	double bsqToXmrRate = xmrMarketPrice.getPrice() / bsqMarketPrice.getPrice();
    	Coin bsqSecurityDeposit = XmrCoin.fromXmrCoin2Coin(dataModel.getSecurityDeposit(), "BSQ", String.valueOf(bsqToXmrRate));
    	Coin bsqAmount = XmrCoin.fromXmrCoin2Coin(dataModel.getAmount().get(), "BSQ", String.valueOf(bsqToXmrRate)); 
        return bsqFormatter.formatBSQSatoshisWithCode(bsqSecurityDeposit.value) +
                GUIUtil.getPercentageOfTradeAmount(bsqSecurityDeposit, bsqAmount);
    }

    public String getSecurityDepositWithCode() {
        return xmrFormatter.formatCoinWithCode(dataModel.getSecurityDeposit());
    }

    public String getBsqSecurityDepositWithCode() {
        double bsqToXmrRate = xmrMarketPrice.getPrice() / bsqMarketPrice.getPrice();

        return bsqFormatter.formatBSQSatoshisWithCode(XmrCoin.fromXmrCoin2Coin(dataModel.getSecurityDeposit(), "BSQ", String.valueOf(bsqToXmrRate)).value);
    }

    public String getTradeFee() {
        //TODO use last bisq market price to estimate BSQ val
        final Coin makerFeeAsCoin = dataModel.getMakerFeeInBsq();
        final XmrCoin makerFeeAsXmr = dataModel.getMakerFee();
        final String makerFee = bsqFormatter.formatBSQSatoshisWithCode(makerFeeAsCoin.value);
        if (dataModel.isCurrencyForMakerFeeXmr())
            return makerFee + GUIUtil.getPercentageOfTradeAmount(makerFeeAsXmr, dataModel.getAmount().get());
        else
            return makerFee + " (" + Res.get("shared.tradingFeeInXmrInfo", xmrFormatter.formatCoinWithCode(makerFeeAsXmr)) + ")";
    }

    public String getBsqTradeFee() {
        double bsqToXmrRate = xmrMarketPrice.getPrice() / bsqMarketPrice.getPrice();
        final Coin makerFeeAsCoin = dataModel.getMakerFeeInBsq();
        final String makerFee = bsqFormatter.formatBSQSatoshisWithCode(makerFeeAsCoin.value);
            
        return makerFee + " (" + Res.get("shared.tradingFeeInBsqInfo", bsqFormatter.formatBSQSatoshisWithCode(makerFeeAsCoin.value)) + ")";
    }

    public String getMakerFeePercentage() {
        final XmrCoin makerFeeAsCoin = dataModel.getMakerFee();
        if (dataModel.isCurrencyForMakerFeeXmr())
            return GUIUtil.getPercentage(makerFeeAsCoin, dataModel.getAmount().get());
        else
            return Res.get("dao.paidWithBsq");
    }

    public String getTotalToPayInfo() {
        final String totalToPay = this.totalToPay.get();
        if (dataModel.isCurrencyForMakerFeeXmr())
            return totalToPay;
        else
            return totalToPay + " + " + xmrFormatter.formatCoinWithCode(dataModel.getMakerFee());
    }

    public String getBsqTotalToPayInfo() {
        double bsqToXmrRate = xmrMarketPrice.getPrice() / bsqMarketPrice.getPrice();
        XmrCoin totalToPayXmr = dataModel.totalToPayAsCoinProperty().get();
        Coin totalToPayBsq = XmrCoin.fromXmrCoin2Coin(totalToPayXmr, "BSQ", String.valueOf(bsqToXmrRate));
    	String totalBsqToPay = bsqFormatter.formatBSQSatoshisWithCode(totalToPayBsq.value);
        return totalBsqToPay + " + " + bsqFormatter.formatBSQSatoshisWithCode(dataModel.getMakerFeeInBsq().value);
    }

    public String getFundsStructure() {
        String fundsStructure;
        if (dataModel.isCurrencyForMakerFeeXmr()) {
            fundsStructure = Res.get("createOffer.fundsBox.fundsStructure",
                    getSecurityDepositWithCode(), getMakerFeePercentage(), getTxFeePercentage());
        } else {
            fundsStructure = Res.get("createOffer.fundsBox.fundsStructure.BSQ",
                    getSecurityDepositWithCode(), getTxFeePercentage(), xmrFormatter.formatCoinWithCode(dataModel.getMakerFee()));
        }
        return fundsStructure;
    }

    public String getBsqFundsStructure() {
        String fundsStructure;
            fundsStructure = Res.get("createOffer.fundsBox.fundsStructure.BSQ",
                    getBsqSecurityDepositWithCode(), getBsqTxFeePercentage(), bsqFormatter.formatBSQSatoshisWithCode(dataModel.getMakerFeeInBsq().value));

            return fundsStructure;
    }

    public String getTxFee() {
        XmrCoin txFeeAsCoin = dataModel.getTxFee();
        return xmrFormatter.formatCoinWithCode(txFeeAsCoin) +
                GUIUtil.getPercentageOfTradeAmount(txFeeAsCoin, dataModel.getAmount().get());

    }

    public String getBsqTxFee() {
        double bsqToXmrRate = xmrMarketPrice.getPrice() / bsqMarketPrice.getPrice();
        Coin txFeeAsCoin = XmrCoin.fromXmrCoin2Coin(dataModel.getTxFee(), "BSQ", String.valueOf(bsqToXmrRate));
        Coin bsqAmount = XmrCoin.fromXmrCoin2Coin(dataModel.getAmount().get(), "BSQ", String.valueOf(bsqToXmrRate)); 
        return bsqFormatter.formatBSQSatoshisWithCode(txFeeAsCoin.value) +
                GUIUtil.getPercentageOfTradeAmount(txFeeAsCoin, bsqAmount);

    }

    public String getTxFeePercentage() {
        XmrCoin txFeeAsCoin = dataModel.getTxFee();
        return GUIUtil.getPercentage(txFeeAsCoin, dataModel.getAmount().get());
    }

    public String getBsqTxFeePercentage() {
        double bsqToXmrRate = xmrMarketPrice.getPrice() / bsqMarketPrice.getPrice();
        Coin txFeeAsCoin = XmrCoin.fromXmrCoin2Coin(dataModel.getTxFee(), "BSQ", String.valueOf(bsqToXmrRate));
        Coin amountAsCoin = XmrCoin.fromXmrCoin2Coin(dataModel.getAmount().get(), "BSQ", String.valueOf(bsqToXmrRate));
        return GUIUtil.getPercentage(txFeeAsCoin, amountAsCoin);
    }

    public PaymentAccount getPaymentAccount() {
        return dataModel.getPaymentAccount();
    }

    public String getAmountDescription() {
        return amountDescription;
    }

    public String getDirectionLabel() {
        return directionLabel;
    }

    public String getAddressAsString() {
        return addressAsString;
    }

    public String getPaymentLabel() {
        return paymentLabel;
    }

    public String formatCoin(XmrCoin coin) {
        return xmrFormatter.formatCoin(coin);
    }

    public Offer createAndGetOffer() {
        offer = dataModel.createAndGetOffer();
        return offer;
    }

    public Callback<ListView<PaymentAccount>, ListCell<PaymentAccount>> getPaymentAccountListCellFactory(
            ComboBox<PaymentAccount> paymentAccountsComboBox) {
        return GUIUtil.getPaymentAccountListCellFactory(paymentAccountsComboBox, accountAgeWitnessService);
    }

    public M getDataModel() {
        return dataModel;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setAmountToModel() {
        if (this.amount.get() != null && !this.amount.get().isEmpty()) {
            XmrCoin xmrCoinAmount = DisplayUtils.parseToCoinWith12Decimals(this.amount.get(), xmrFormatter);

            long maxTradeLimit = dataModel.getMaxTradeLimit();
            Price price = dataModel.getPrice().get();
            double btcToXmrRate = xmrMarketPrice.getPrice();
            if (price != null) {
                if (dataModel.isHalCashAccount())
                    xmrCoinAmount = XmrOfferUtil.getAdjustedAmountForHalCash(xmrCoinAmount, price, maxTradeLimit, btcToXmrRate);
                else if (CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get()))
                    xmrCoinAmount = XmrOfferUtil.getRoundedFiatAmount(xmrCoinAmount, price, maxTradeLimit, btcToXmrRate);
            }
            dataModel.setAmount(xmrCoinAmount);
            if (syncMinAmountWithAmount ||
                    dataModel.getMinAmount().get() == null ||
                    dataModel.getMinAmount().get().equals(XmrCoin.ZERO)) {
                minAmount.set(this.amount.get());
                setMinAmountToModel();
            }
        } else {
            dataModel.setAmount(null);
        }
    }

    private void setMinAmountToModel() {
        if (minAmount.get() != null && !minAmount.get().isEmpty()) {
            XmrCoin minAmount = DisplayUtils.parseToCoinWith12Decimals(this.minAmount.get(), xmrFormatter);

            Price price = dataModel.getPrice().get();
            long maxTradeLimit = dataModel.getMaxTradeLimit();
            double btcToXmrRate = xmrMarketPrice.getPrice();
            if (price != null) {
                if (dataModel.isHalCashAccount())
                    minAmount = XmrOfferUtil.getAdjustedAmountForHalCash(minAmount, price, maxTradeLimit, btcToXmrRate);
                else if (CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get()))
                    minAmount = XmrOfferUtil.getRoundedFiatAmount(minAmount, price, maxTradeLimit, btcToXmrRate);
            }

            dataModel.setMinAmount(minAmount);
        } else {
            dataModel.setMinAmount(null);
        }
    }

    private void setPriceToModel() {
        if (price.get() != null && !price.get().isEmpty()) {
            try {
                dataModel.setPrice(Price.parse(dataModel.getTradeCurrencyCode().get(), this.price.get()));
            } catch (Throwable t) {
                log.debug(t.getMessage());
            }
        } else {
            dataModel.setPrice(null);
        }
    }

    private void setVolumeToModel() {
        if (volume.get() != null && !volume.get().isEmpty()) {
            try {
                dataModel.setVolume(Volume.parse(volume.get(), dataModel.getTradeCurrencyCode().get()));
            } catch (Throwable t) {
                log.debug(t.getMessage());
            }
        } else {
            dataModel.setVolume(null);
        }
    }

    private void setBuyerSecurityDepositToModel() {
        if (buyerSecurityDeposit.get() != null && !buyerSecurityDeposit.get().isEmpty()) {
            dataModel.setBuyerSecurityDeposit(ParsingUtils.parsePercentStringToDouble(buyerSecurityDeposit.get()));
        } else {
            dataModel.setBuyerSecurityDeposit(XmrRestrictions.getDefaultBuyerSecurityDepositAsPercent(getPaymentAccount()));
        }
    }

    private InputValidator.ValidationResult isXmrInputValid(String input) {
        return xmrValidator.validate(input);
    }

    private InputValidator.ValidationResult isPriceInputValid(String input) {
        return getPriceValidator().validate(input);
    }

    private InputValidator.ValidationResult isVolumeInputValid(String input) {
        return getVolumeValidator().validate(input);
    }

    private MonetaryValidator getPriceValidator() {
        return CurrencyUtil.isCryptoCurrency(getTradeCurrency().getCode()) ? altcoinValidator : fiatPriceValidator;
    }

    private MonetaryValidator getVolumeValidator() {
        final String code = getTradeCurrency().getCode();
        if (CurrencyUtil.isCryptoCurrency(code)) {
            return code.equals("BSQ") ? bsqValidator : altcoinValidator;
        } else {
            return fiatVolumeValidator;
        }
    }

    private void updateSpinnerInfo() {
        if (!showPayFundsScreenDisplayed.get() ||
                errorMessage.get() != null ||
                showTransactionPublishedScreen.get()) {
            waitingForFundsText.set("");
        } else if (dataModel.getIsXmrWalletFunded().get()) {
            waitingForFundsText.set("");
           /* if (dataModel.isFeeFromFundingTxSufficient.get()) {
                spinnerInfoText.set("");
            } else {
                spinnerInfoText.set("Check if funding tx miner fee is sufficient...");
            }*/
        } else {
            waitingForFundsText.set(Res.get("shared.waitingForFunds"));
        }

        isWaitingForFunds.set(!waitingForFundsText.get().isEmpty());
    }

    private void updateButtonDisableState() {
        log.debug("updateButtonDisableState");
        boolean inputDataValid = isXmrInputValid(amount.get()).isValid &&
                isXmrInputValid(minAmount.get()).isValid &&
                isPriceInputValid(price.get()).isValid &&
                securityDepositValidator.validate(buyerSecurityDeposit.get()).isValid &&
                dataModel.getPrice().get() != null &&
                dataModel.getPrice().get().getValue() != 0 &&
                isVolumeInputValid(volume.get()).isValid &&
                isVolumeInputValid(DisplayUtils.formatVolume(dataModel.getMinVolume().get())).isValid &&
                dataModel.isMinAmountLessOrEqualAmount();

        isNextButtonDisabled.set(!inputDataValid);
        // boolean notSufficientFees = dataModel.isWalletFunded.get() && dataModel.isMainNet.get() && !dataModel.isFeeFromFundingTxSufficient.get();
        //isPlaceOfferButtonDisabled.set(createOfferRequested || !inputDataValid || notSufficientFees);
        isPlaceOfferButtonDisabled.set(createOfferRequested || !inputDataValid || !dataModel.getIsXmrWalletFunded().get());
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }

    private CoinFormatter getFormatterForMakerFee(Object coin) {
        return coin instanceof XmrCoin ? xmrFormatter : bsqFormatter;
    }

    private CoinFormatter getFormatterForMakerFee() {
        return dataModel.isCurrencyForMakerFeeXmr() ? xmrFormatter : bsqFormatter;
    }

}
