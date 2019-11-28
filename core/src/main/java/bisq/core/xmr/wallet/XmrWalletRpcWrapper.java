package bisq.core.xmr.wallet;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InetAddresses;

import bisq.asset.CryptoNoteAddressValidator;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.core.btc.listeners.AddressConfidenceListener;
import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.locale.Res;
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
import javafx.application.Platform;
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
	
	//TODO(niyid) onChangeListener to dynamically create and set new walletRpc instance.
	//TODO(niyid) onChangeListener fires only after any of host, port, user, password have changed
	//TODO(niyid) Only allow testnet, stagenet connections in dev/test. Only mainnet allowed in prod.
	//TODO(niyid) ***ABSOLUTELY CRITICAL!!!*** Only allow Mainnet XMR Wallet when Bisq is on Mainnet

    @Inject
    public XmrWalletRpcWrapper(Preferences preferences) {
    	this.preferences = preferences;
		log.debug("instantiating MoneroWalletRpc...");
		HOST = preferences.getXmrUserHostDelegate();
		PORT = Integer.parseInt(preferences.getXmrHostPortDelegate());
        //TODO(niyid) Use preferences to determine which wallet to load in XmrWalletRpcWrapper		
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
				walletRpc.close();
				walletRpc = null;
			}
/**/			
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
					Address address = walletRpc.createWallet(accountIndex, label);
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
            		walletRpc = new MoneroWalletRpc(new MoneroRpcConnection("http://" + HOST + ":" + PORT, preferences.getXmrRpcUserDelegate(), preferences.getXmrRpcPwdDelegate()));
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

	public MoneroWalletRpc getWalletRpc() {
    	return walletRpc;
    }
	
	public String getPrimaryAddress() {
		if(primaryAddress == null) {
			walletRpc = new MoneroWalletRpc(new MoneroRpcConnection("http://" + HOST + ":" + PORT, preferences.getXmrRpcUserDelegate(), preferences.getXmrRpcPwdDelegate()));
			primaryAddress = walletRpc.getPrimaryAddress();
		}
		return primaryAddress;
	}

	public XmrCoin getBalanceForAddress(String address) {
		return XmrCoin.valueOf(walletRpc.getBalance(address).longValueExact());
	}

	public Address getOrCreateAddressEntry(String offerId) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(InetAddresses.forString(HOST), PORT), 5000);
            log.info("Localhost Monero Wallet RPC detected.");
            UserThread.execute(() -> {
        		walletRpc = new MoneroWalletRpc(new MoneroRpcConnection("http://" + HOST + ":" + PORT, preferences.getXmrRpcUserDelegate(), preferences.getXmrRpcPwdDelegate()));
            });
        } catch (Throwable e) {
        	log.error("createWalletRpcInstance - {}", e.getMessage());
        	e.printStackTrace();
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignore) {
                }
            }
        }
		return walletRpc != null ? walletRpc.createWallet(0, offerId) : null;
	}

	public Address getOrCreateAddressEntry() {
		return getOrCreateAddressEntry("Bisq Offer " + System.currentTimeMillis());
	}

	public void addBalanceListener(XmrBalanceListener xmrBalanceListener) {
		balanceListeners.add(xmrBalanceListener);
	}

	public void removeBalanceListener(XmrBalanceListener xmrBalanceListener) {
		balanceListeners.remove(xmrBalanceListener);
	}
	
	public XmrCoin getBalance() {
		return XmrCoin.valueOf(walletRpc.getBalance().longValueExact());
	}

	public void resetAddressEntriesForOpenOffer(String id) {
		//TODO(niyid) To be implemented if at all
		
	}
}
