package bisq.core.xmr.wallet;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InetAddresses;

import bisq.asset.CryptoNoteAddressValidator;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.PubKeyRing;
import bisq.core.btc.listeners.AddressConfidenceListener;
import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Attachment;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.traderchat.TradeChatSession;
import bisq.core.support.traderchat.TraderChatManager;
import bisq.core.trade.Trade;
import bisq.core.user.Preferences;
import bisq.core.xmr.XmrCoin;
import bisq.core.xmr.jsonrpc.MoneroRpcConnection;
import bisq.core.xmr.jsonrpc.MoneroSendPriority;
import bisq.core.xmr.jsonrpc.MoneroWalletRpc;
import bisq.core.xmr.jsonrpc.result.Address;
import bisq.core.xmr.jsonrpc.result.MoneroTransfer;
import bisq.core.xmr.jsonrpc.result.MoneroTx;
import bisq.core.xmr.listeners.XmrBalanceListener;
import bisq.core.xmr.wallet.listeners.WalletUiListener;
import bisq.network.p2p.P2PService;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class XmrWalletRpcWrapper {
	public static String HOST = "127.0.0.1";
	public static int PORT = 29088;
	protected final Logger log = LoggerFactory.getLogger(this.getClass());
	private MoneroWalletRpc walletRpc;
	private String primaryAddress;
	private Preferences preferences;
    protected final CopyOnWriteArraySet<AddressConfidenceListener> addressConfidenceListeners = new CopyOnWriteArraySet<>();
    protected final CopyOnWriteArraySet<TxConfidenceListener> txConfidenceListeners = new CopyOnWriteArraySet<>();
    protected final CopyOnWriteArraySet<XmrBalanceListener> balanceListeners = new CopyOnWriteArraySet<>();
    private TraderChatManager traderChatManager;
    private PubKeyRing pubKeyRing;
    private P2PService p2PService;
    private ChangeListener<Boolean> arrivedPropertyListener;
    private ChatMessage multisigInfoMessage;
    private HashMap<String, HashMap<String, String>> tradeData = new HashMap<>();
	//TODO(niyid) onChangeListener to dynamically create and set new walletRpc instance.
	//TODO(niyid) onChangeListener fires only after any of host, port, user, password have changed
	//TODO(niyid) Only allow testnet, stagenet connections in dev/test. Only mainnet allowed in prod.
	//TODO(niyid) ***ABSOLUTELY CRITICAL!!!*** Only allow Mainnet XMR Wallet when Bisq is on Mainnet

    @Inject
    public XmrWalletRpcWrapper(Preferences preferences, TraderChatManager traderChatManager, PubKeyRing pubKeyRing, P2PService p2PService) {
    	this.preferences = preferences;
    	this.traderChatManager = traderChatManager;
    	this.pubKeyRing = pubKeyRing;
    	this.p2PService = p2PService;
		HOST = preferences.getXmrUserHostDelegate();
		PORT = Integer.parseInt(preferences.getXmrHostPortDelegate());
        //TODO(niyid) Use preferences to determine which wallet to load in XmrWalletRpcWrapper		
		try {
			log.info("instantiating MoneroWalletRpc...");
			openWalletRpcInstance(null);
			if(isXmrWalletRpcRunning()) {
				//TODO(niyid) Uncomment later
	/**/			
				CryptoNoteAddressValidator validator;
				long[] validPrefixes = {};
				if(DevEnv.isDevMode()) {
	            	validPrefixes = new long[]{24, 36, 53, 63};
	            	validator = new CryptoNoteAddressValidator(true, validPrefixes);
				} else {
	            	validPrefixes = new long[]{18, 42};
	            	validator = new CryptoNoteAddressValidator(true, validPrefixes);
				}
				if(!validator.validate(primaryAddress).isValid()) {
					log.debug("Wallet RPC Connection not valid (MAINNET/TESTNET mix-up); shutting down...");
					walletRpc.closeWallet();
					walletRpc = null;
				} else {
			        arrivedPropertyListener = (observable, oldValue, newValue) -> {
			            if (newValue) {
			            	if(SupportType.TRADE_XMR_MULTISIG.equals(multisigInfoMessage.getSupportType())) {
			            		prepareTradeBob(multisigInfoMessage.getTradeId(), multisigInfoMessage.getMessage());
			            	}
			            }
			        };
					
				}
	/**/			
			}
		} catch (Exception e) {
			log.error("Connection to Monero Wallet using monero-wallet-rpc failed.");
		}
    }
        
    public void update(WalletUiListener listener, HashMap<String, Object> walletRpcData) { 
		Runnable command = new Runnable() {
			
			@Override
			public void run() {
		    	checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
		    	listener.playAnimation();
				if(walletRpcData != null) {
					long time0;
					if(walletRpcData.containsKey("getBalance")) {
						time0 = System.currentTimeMillis();
						BigInteger balance = walletRpc.getBalance();
						walletRpcData.put("getBalance", balance);
						log.debug("listen -time: {}ms - balance: {}", (System.currentTimeMillis() - time0), balance);
					}
					if(walletRpcData.containsKey("getUnlockedBalance")) {
						time0 = System.currentTimeMillis();
						BigInteger unlockedBalance = walletRpc.getUnlockedBalance();
						walletRpcData.put("getUnlockedBalance", unlockedBalance);
						log.debug("listen -time: {}ms - unlockedBalance: {}", (System.currentTimeMillis() - time0));
					}
					if(walletRpcData.containsKey("getPrimaryAddress")) {
						time0 = System.currentTimeMillis();
						primaryAddress = walletRpc.getPrimaryAddress();
						walletRpcData.put("getPrimaryAddress", primaryAddress);
						log.debug("listen -time: {}ms - address: {}", (System.currentTimeMillis() - time0), primaryAddress);
					}
					if(walletRpcData.containsKey("getTxs")) {
						time0 = System.currentTimeMillis();
						List<MoneroTransfer> txList = walletRpc.getTxs(null);
						if(txList != null && !txList.isEmpty()) {
							walletRpcData.put("getTxs", transformTxWallet(txList));
							log.debug("listen -time: {}ms - transactions: {}", (System.currentTimeMillis() - time0), txList.size());
						} else {
							List<XmrTxListItem> list = Collections.emptyList();
							walletRpcData.put("getTxs", list);
						}
					}
				}
				listener.onUpdateBalances(walletRpcData);
				listener.stopAnimation();
			}
		};
		try {
			Platform.runLater(command);
		} catch (Exception e) {
			listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed"));
		}
    }
    
    private List<XmrTxListItem> transformTxWallet(List<MoneroTransfer> txList) {
		Predicate<MoneroTransfer> predicate = new Predicate<>() {

			@Override
			public boolean test(MoneroTransfer t) {
				//Check if transaction occurred less than 90 days ago
				return (new Date().getTime() - Date.from(Instant.ofEpochSecond(t.getTimestamp())).getTime()) <= 90 * 24 * 3600 * 1000l;
			}
		};
    	List<XmrTxListItem> list = new ArrayList<>();
		txList.stream().filter(predicate).forEach(txWallet -> list.add(new XmrTxListItem(txWallet)));
    	log.debug("transformTxWallet => {}", list.size());

    	return list.size() > 100 ? list.subList(0, 100) : list;//Reduce transactions to no more than 100.
    }
    
    public void searchTx(WalletUiListener listener, String commaSeparatedIds) {
		Runnable command = new Runnable() {
			
			@Override
			public void run() {
		    	checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
				HashMap<String, Object> walletRpcData = new HashMap<>();
				listener.playAnimation();
				if(commaSeparatedIds != null && !commaSeparatedIds.isEmpty()) {
					String searchParam = commaSeparatedIds.replaceAll(" ", "");
					
					searchParam.split(",");
					long time0 = System.currentTimeMillis();
					List<MoneroTransfer> txs = walletRpc.getTxs(searchParam);
					walletRpcData.put("getTxs", transformTxWallet(txs));
					log.debug("listen -time: {}ms - searchTx: {}", (System.currentTimeMillis() - time0), txs.size());
				}
				listener.onUpdateBalances(walletRpcData);
				listener.stopAnimation();
			}
		};
		try {
			Platform.runLater(command);
		} catch (Exception e) {
        	listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed"));
		}
    }
    
    public void createTx(WalletUiListener listener, Integer accountIndex, String address, 
    		BigInteger amount, MoneroSendPriority priority, boolean doNotRelay, HashMap<String, Object> walletRpcData) { 
		Runnable command = new Runnable() {
			
			@Override
			public void run() {
				checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
				listener.playAnimation();
				long time0 = System.currentTimeMillis();
				Map<String, Object> destination = new HashMap<>();
				destination.put("amount", amount);
				destination.put("address", address);
				List<Map<String, Object>> destinations = new ArrayList<Map<String,Object>>();
				destinations.add(destination);
				Map<String, Object> request = new HashMap<>();
				request.put("destinations", destinations);
				request.put("priority", priority.ordinal());
				request.put("payment_id", MoneroWalletRpc.generatePaymentId());
				request.put("get_tx_key", true);
				request.put("get_tx_hex", false);
				request.put("do_not_relay", true);
				request.put("get_tx_metadata", true);
				MoneroTx tx = walletRpc.send(request);
				
				walletRpcData.put("getBalance", walletRpc.getBalance());
				walletRpcData.put("getUnlockedBalance", walletRpc.getUnlockedBalance());
				walletRpcData.put("getFee", tx.getFee());
				walletRpcData.put("getAmount", tx.getAmount());
				walletRpcData.put("getAddress", address);
				walletRpcData.put("getSize", tx.getSize());
				if(doNotRelay) {
					walletRpcData.put("txToRelay", tx.getTxMetadata());
				}
				log.debug("MoneroTxWallet => {}", walletRpcData);
				log.debug("createTx -time: {}ms - createTx: {}", (System.currentTimeMillis() - time0), tx.getSize());
				listener.onUpdateBalances(walletRpcData);
				listener.stopAnimation();
			}
		};
		try {
			Platform.runLater(command);
		} catch (Exception e) {
			listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed"));
		}
    }
    
    public void relayTx(WalletUiListener listener, HashMap<String, Object> walletRpcData) { 
		Runnable command = new Runnable() {
			
			@Override
			public void run() {
				checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
				listener.playAnimation();
				String txToRelay = (String) walletRpcData.get("txToRelay");
				if(txToRelay != null) {
					long time0 = System.currentTimeMillis();
					String txId = walletRpc.relayTx(txToRelay);
					walletRpcData.put("txId", txId);
					walletRpcData.put("getMetadata", txToRelay);
					log.debug("relayTx metadata: {}", txToRelay);
					log.debug("relayTx -time: {}ms - txId: {}", (System.currentTimeMillis() - time0), txId);
				}
				listener.stopAnimation();
			}
		};
		try {
			Platform.runLater(command);
		} catch (Exception e) {
			listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed"));
		}
    }
    
    public void createWallet(WalletUiListener listener, int accountIndex, String label) { 
		Runnable command = new Runnable() {
			
			@Override
			public void run() {
				checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
				listener.playAnimation();
					long time0 = System.currentTimeMillis();
					Address address = walletRpc.createAccountAddress(accountIndex, label);
					log.debug("relayTx -time: {}ms - txId: {}", (System.currentTimeMillis() - time0), address.getAddress());
				listener.stopAnimation();
			}
		};
		try {
			Platform.runLater(command);
		} catch (Exception e) {
			listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed"));
		}
    }
    
    public void openWalletRpcInstance(WalletUiListener listener) {
    	log.debug("openWalletRpcInstance - {}, {}", HOST, PORT);
        Thread checkIfXmrLocalHostNodeIsRunningThread = new Thread(() -> {
            Thread.currentThread().setName("checkIfXmrLocalHostNodeIsRunningThread");
            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(InetAddresses.forString(HOST), PORT), 5000);
                log.debug("Localhost Monero Wallet RPC detected.");
                UserThread.execute(() -> {
            		initWalletRpc();
                });
            } catch (Throwable e) {
            	log.debug("createWalletRpcInstance - {}", e.getMessage());
            	e.printStackTrace();
            	if(listener != null) {
           			listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed", "Monero", e.getLocalizedMessage()));
            	}
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignore) {
                    }
                }
            }
        });
        checkIfXmrLocalHostNodeIsRunningThread.start();
    }
    
    public boolean isXmrWalletRpcRunning() {
    	initWalletRpc();
    	return walletRpc != null;
    }
	
	public void handleTxProof(TxProofHandler handler, String txId, String message) {
		Runnable command = new Runnable() {
			
			@Override
			public void run() {
				checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
				handler.playAnimation();
				if(handler != null) {
					long time0 = System.currentTimeMillis();
					String signature = walletRpc.getSpendProof(txId, message);
					log.debug("relayTx -time: {}ms - txId: {}", (System.currentTimeMillis() - time0), txId);
					log.debug("relayTx signature: {}", signature);
					handler.update(txId, message, signature);
				}
				handler.stopAnimation();
			}
		};
		try {
			Platform.runLater(command);
		} catch (Exception e) {
			handler.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed"));
		}		
	}
	
	private void initWalletRpc() {
		if(walletRpc == null) {
			walletRpc = new MoneroWalletRpc(new MoneroRpcConnection("http://" + HOST + ":" + PORT, preferences.getXmrRpcUserDelegate(), preferences.getXmrRpcPwdDelegate()));
		}
		checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
	}

	public MoneroWalletRpc getWalletRpc() {
    	return walletRpc;
    }
	
	public String getPrimaryAddress() {
		if(primaryAddress == null) {
			initWalletRpc();
			primaryAddress = walletRpc.getPrimaryAddress();
		}
		return primaryAddress;
	}

	public XmrCoin getBalanceForAddress(String address) {
		return XmrCoin.valueOf(walletRpc.getBalance(address).longValueExact());
	}

	public void addBalanceListener(XmrBalanceListener xmrBalanceListener) {
		balanceListeners.add(xmrBalanceListener);
	}

	public void removeBalanceListener(XmrBalanceListener xmrBalanceListener) {
		balanceListeners.remove(xmrBalanceListener);
	}
	
	public XmrCoin getBalance() {
		initWalletRpc();
		return XmrCoin.valueOf(walletRpc.getBalance().longValueExact());
	}
	
	//Step 1: Method called by taker and then maker
	public void prepareTradeAlice(Trade trade, boolean isMaker) {
		initWalletRpc();
		
		Offer offer = trade.getOffer();
		String walletPassword = generateCommonLangPassword();//TODO(niyid) This has to be stored somewhere safe
		//TODO(niyid) Use offer to know which language to use for wallet seed
		walletRpc.createWallet(offer.getId(), walletPassword, "English"); //TODO(niyid) Create 2/2 multisig wallet for taker - create_wallet
		walletRpc.openWallet(offer.getId(), walletPassword);
		String multisigInfoAlice = walletRpc.prepareMultisig();//TODO(niyid) Prepare multisig wallet - prepare_multisig
		sendMultisigInfo(trade, isMaker, multisigInfoAlice, pubKeyRing);
		walletRpc.makeMultisig(new String[] {multisigInfoAlice}, 2, walletPassword);//TODO(niyid)
		HashMap<String, String> data = new HashMap<>();
		data.put("multisigInfo", multisigInfoAlice);
		tradeData.put(trade.getId(), data);
	}
	
	public void prepareTradeBob(String tradeId, String multisigInfoBob) {
		String walletPassword = generateCommonLangPassword();//TODO(niyid) This has to be stored somewhere safe
		walletRpc.importMultisigInfo(new String[] {multisigInfoBob});
		walletRpc.finalizeMultisig(new String[] {multisigInfoBob, tradeData.get(tradeId).get("multisigInfo")}, walletPassword);
		
	}
	
	//Step 2: Method called by maker
	public void holdOfferAmount(Offer offer) {
		//TODO(niyid) This is the transaction that transfers and holds the XMR amount in the multisig wallet (Combine with processOffer)?
//		walletRpc.send(request);
		//TODO(niyid) Maker create a tx to move the offer amount equivalent from the primary wallet address to the multisig wallet - transfer
		//TODO(niyid) Maker signs tx - sign_multisig
		//TODO(niyid) Maker submits tx - submit_multisig
	}
	
	//Step 3 to 4: Method called by maker
	public void completeTrade(Offer offer) {
		//TODO(niyid) This is the transaction that transfers the offer held in the multisig wallet to the taker on maker indicating receipt...
		//TODO(niyid) ...of trade (of what ever currency and through what ever medium) from taker.
//		walletRpc.send(request);
//		walletRpc.signMultisig(txDataHex);
//		walletRpc.submitMultisig(txDataHex);
		//TODO(niyid) Maker creates a tx to send the amount held in the multisig wallet to the taker - transfer
		//TODO(niyid) Maker signs tx - sign_multisig
		//TODO(niyid) Maker submits  tx - submit_multisig
	}
	
	private void sendMultisigInfo(Trade trade, boolean isMaker, String multisigInfo, PubKeyRing pubKeyRing) {
		multisigInfoMessage = new ChatMessage(
        		SupportType.TRADE_XMR_MULTISIG,
        		trade.getId(),
        		pubKeyRing.hashCode(),
                !isMaker,
                multisigInfo,
                p2PService.getAddress(),
                new ArrayList<Attachment>()
        );
		multisigInfoMessage.arrivedProperty().addListener(arrivedPropertyListener);
        traderChatManager.addAndPersistChatMessage(multisigInfoMessage);
        traderChatManager.sendChatMessage(multisigInfoMessage);
	}
	
	private String generateCommonLangPassword() {
	    String upperCaseLetters = RandomStringUtils.random(2, 65, 90, true, true);
	    String lowerCaseLetters = RandomStringUtils.random(2, 97, 122, true, true, null, new SecureRandom());
	    String numbers = RandomStringUtils.randomNumeric(2);
	    String specialChar = RandomStringUtils.random(2, 33, 47, false, false);
	    String totalChars = RandomStringUtils.randomAlphanumeric(2);
	    String combinedChars = upperCaseLetters.concat(lowerCaseLetters)
	      .concat(numbers)
	      .concat(specialChar)
	      .concat(totalChars);
	    List<Character> pwdChars = combinedChars.chars()
	      .mapToObj(c -> (char) c)
	      .collect(Collectors.toList());
	    Collections.shuffle(pwdChars);
	    String password = pwdChars.stream()
	      .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
	      .toString();
	    return password;
	}
}
