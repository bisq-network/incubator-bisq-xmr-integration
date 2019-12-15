package bisq.core.xmr.jsonrpc;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bisq.core.xmr.jsonrpc.result.Address;
import bisq.core.xmr.jsonrpc.result.AddressIndex;
import bisq.core.xmr.jsonrpc.result.Balance;
import bisq.core.xmr.jsonrpc.result.MoneroTransfer;
import bisq.core.xmr.jsonrpc.result.MoneroTransferList;
import bisq.core.xmr.jsonrpc.result.MoneroTx;

public class MoneroWalletRpc {
	
	protected Logger log = LoggerFactory.getLogger(MoneroWalletRpc.class);
	
	private MoneroRpcConnection rpcConnection; 

	public MoneroWalletRpc(MoneroRpcConnection rpcConnection) {
		this.rpcConnection = rpcConnection;
	}
	
	public String getPrimaryAddress() {
//		Map<String, Object> params = new HashMap<>();
//		params.put("account_index", 0);
		Map<String, Object> response = rpcConnection.sendJsonRequest("get_address");
		log.debug("response => {}", response);
		
		Address address = JsonUtils.deserialize(MoneroRpcConnection.DEFAULT_MAPPER, JsonUtils.serialize(MoneroRpcConnection.DEFAULT_MAPPER, response.get("result")), Address.class);
		log.debug("address => {}", address);
		
		return address.getAddress();
	}
	
	public Balance getBalanceData() {
		Map<String, Object> response = rpcConnection.sendJsonRequest("get_balance");
		log.debug("response => {}", response);
		
		Balance balance = JsonUtils.deserialize(MoneroRpcConnection.DEFAULT_MAPPER, JsonUtils.serialize(MoneroRpcConnection.DEFAULT_MAPPER, response.get("result")), Balance.class);
		log.debug("balance => {}", balance);
		
		return balance;
	}
	
	public AddressIndex getAddressIndex(String address) {
		Map<String, Object> params = new HashMap<>();
		params.put("address", address);
		Map<String, Object> response = rpcConnection.sendJsonRequest("get_address_index", params);
		log.debug("response => {}", response);
		
		AddressIndex index = JsonUtils.deserialize(MoneroRpcConnection.DEFAULT_MAPPER, JsonUtils.serialize(MoneroRpcConnection.DEFAULT_MAPPER, response.get("result")), AddressIndex.class);
		log.debug("index => {}", index);
		
		return index;
	}
	
	public Balance getBalanceData(String address) {
		AddressIndex index = getAddressIndex(address);
		Map<String, Object> params = new HashMap<>();
		params.put("account_index", index.getMajor());
		params.put("address_index", new long[]{index.getMinor()});
		Map<String, Object> response = rpcConnection.sendJsonRequest("get_balance", params);
		log.debug("response => {}", response);
		
		Balance balance = JsonUtils.deserialize(MoneroRpcConnection.DEFAULT_MAPPER, JsonUtils.serialize(MoneroRpcConnection.DEFAULT_MAPPER, response.get("result")), Balance.class);
		log.debug("balance => {}", balance);
		
		return balance;
	}
	
	public BigInteger getBalance() {
		return getBalanceData().getBalance();
	}
	
	public BigInteger getBalance(String address) {
		return getBalanceData(address).getBalance();
	}
	
	public BigInteger getUnlockedBalance() {
		return getBalanceData().getUnlockedBalance();
	}
	
	public MoneroTx send(Map<String, Object> request) {
		Map<String, Object> response = rpcConnection.sendJsonRequest("transfer", request);
		log.debug("response => {}", response);

		MoneroTx moneroTx = JsonUtils.deserialize(MoneroRpcConnection.DEFAULT_MAPPER, JsonUtils.serialize(MoneroRpcConnection.DEFAULT_MAPPER, response.get("result")), MoneroTx.class);
		log.debug("moneroTx => {}", moneroTx);
		
		return moneroTx;
	}
	
	@SuppressWarnings("unchecked")
	public String relayTx(String txMetadata) {
		Map<String, Object> params = new HashMap<>();
		params.put("hex", txMetadata);
		Map<String, Object> response = rpcConnection.sendJsonRequest("relay_tx", params);
		log.debug("response => {}", response);
		
		Map<String, Object> result = (Map<String, Object>) response.get("result");
		String txHash = (String) result.get("tx_hash");
		log.debug("txHash => {}", txHash);
		
		return txHash;
	}
	
	public Address createAccountAddress(int accountIndex, String label) {
		Map<String, Object> params = new HashMap<>();
		params.put("account_index", accountIndex);
		params.put("label", label);
		Map<String, Object> response = rpcConnection.sendJsonRequest("create_address", params);
		log.debug("response => {}", response);
		
		return JsonUtils.deserialize(MoneroRpcConnection.DEFAULT_MAPPER, JsonUtils.serialize(MoneroRpcConnection.DEFAULT_MAPPER, response.get("result")), Address.class);
	}
	
	public List<MoneroTransfer> getTxs(String txIds) {
		Map<String, Object> params = new HashMap<>();
		Map<String, Object> response = null;
		List<MoneroTransfer> transfers = new ArrayList<>();
		if(txIds == null || txIds.isEmpty()) {
			params.put("in", true);
			params.put("out", true);
			params.put("pending", true);
			params.put("failed", true);
			params.put("pool", true);
			params.put("in", true);
			params.put("in", true);
			params.put("in", true);
			response = rpcConnection.sendJsonRequest("get_transfers", params);	
			log.debug("response => {}", response);
			MoneroTransferList transferList = JsonUtils.deserialize(MoneroRpcConnection.DEFAULT_MAPPER, JsonUtils.serialize(MoneroRpcConnection.DEFAULT_MAPPER, response.get("result")), MoneroTransferList.class);
			transfers.addAll(transferList.getIn());
			transfers.addAll(transferList.getOut());
			transfers.addAll(transferList.getPending());
			transfers.addAll(transferList.getPool());
			transfers.addAll(transferList.getFailed());
		} else {
			String[] txIdTokens = txIds.replace(" ", "").split(",") ;
			for(String tid : txIdTokens) {
				params = new HashMap<>();
				params.put("txid", tid);
				response = rpcConnection.sendJsonRequest("get_transfer_by_txid", params);
				log.debug("response => {}", response);
				MoneroTransfer transfer = JsonUtils.deserialize(MoneroRpcConnection.DEFAULT_MAPPER, JsonUtils.serialize(MoneroRpcConnection.DEFAULT_MAPPER, response.get("transfer")), MoneroTransfer.class);
				transfers.add(transfer);
			}
		}
		
		log.debug("transfers => {}", transfers);
		
		return transfers;
	}
	
	@SuppressWarnings("unchecked")
	public String getSpendProof(String txId, String message) {
		Map<String, Object> params = new HashMap<>();
		params.put("txid", txId);
		if(message != null && !message.isEmpty() ) {
			params.put("message", message);
		}
		Map<String, Object> response = rpcConnection.sendJsonRequest("get_spend_proof", params);
		log.debug("response => {}", response);

		Map<String, Object> result = (Map<String, Object>) response.get("result");
		String signature = (String) result.get("signature");
		log.debug("signature => {}", signature);
		
		return signature;
		
	}
	
	@SuppressWarnings("unchecked")
	public boolean checkSpendProof(String txId, String message, String signature) {
		Map<String, Object> params = new HashMap<>();
		params.put("txid", txId);
		if(message != null && !message.isEmpty() ) {
			params.put("message", message);
		}
		params.put("signature", signature);
		Map<String, Object> response = rpcConnection.sendJsonRequest("get_spend_proof", params);
		log.debug("response => {}", response);

		Map<String, Object> result = (Map<String, Object>) response.get("result");
		boolean good = (boolean) result.get("good");
		log.debug("good => {}", good);
		
		return good;
		
	}
	
	@SuppressWarnings("unchecked")
	public void createWallet(String walletFileName, String password, String language) {
		Map<String, Object> params = new HashMap<>();
		params.put("filename", walletFileName);
		params.put("password", password);
		params.put("language", language);
		Map<String, Object> response = rpcConnection.sendJsonRequest("create_Wallet", params);
		log.debug("response => {}", response);
		
		Map<String, Object> result = (Map<String, Object>) response.get("result");
	}
	
	@SuppressWarnings("unchecked")
	public String prepareMultisig() {
		Map<String, Object> response = rpcConnection.sendJsonRequest("prepare_multisig");
		log.debug("response => {}", response);
		
		Map<String, Object> result = (Map<String, Object>) response.get("result");
		
		return (String) result.get("multisig_info");
	}
	
	@SuppressWarnings("unchecked")
	public Integer importMultisigInfo(String[] multisigInfo) {
		Map<String, Object> params = new HashMap<>();
		params.put("info", multisigInfo);
		Map<String, Object> response = rpcConnection.sendJsonRequest("import_multisig_info", params);
		log.debug("response => {}", response);
		
		
		Map<String, Object> result = (Map<String, Object>) response.get("result");
		
		return (Integer) result.get("info");
	}
	
	@SuppressWarnings("unchecked")
	public String exportMultisigInfo() {
		Map<String, Object> response = rpcConnection.sendJsonRequest("prepare_multisig");
		log.debug("response => {}", response);
		
		Map<String, Object> result = (Map<String, Object>) response.get("result");
		
		return (String) result.get("info");
	}
	
	@SuppressWarnings("unchecked")
	public String finalizeMultisig(String[] multisigInfo, String password) {
		Map<String, Object> params = new HashMap<>();
		params.put("info", multisigInfo);
		params.put("password", password);
		Map<String, Object> response = rpcConnection.sendJsonRequest("finalize_multisig", params);
		log.debug("response => {}", response);
		
		Map<String, Object> result = (Map<String, Object>) response.get("result");
		
		return (String) result.get("address");
	}
	
	public String makeMultisig(String[] multisigInfo, int threshold, String password) {
		Map<String, Object> params = new HashMap<>();
		params.put("multisig_info", multisigInfo);
		params.put("threshold", threshold);
		params.put("password", password);
		Map<String, Object> response = rpcConnection.sendJsonRequest("make_multisig", params);
		log.debug("response => {}", response);
		
		return JsonUtils.deserialize(MoneroRpcConnection.DEFAULT_MAPPER, JsonUtils.serialize(MoneroRpcConnection.DEFAULT_MAPPER, response.get("result")), String.class);
	}
	
	public String signMultisig(String txDataHex) {
		Map<String, Object> params = new HashMap<>();
		params.put("tx_data_hex", txDataHex);
		Map<String, Object> response = rpcConnection.sendJsonRequest("sign_multisig", params);
		log.debug("response => {}", response);
		
		return JsonUtils.deserialize(MoneroRpcConnection.DEFAULT_MAPPER, JsonUtils.serialize(MoneroRpcConnection.DEFAULT_MAPPER, response.get("result")), String.class);
	}
	
	public String submitMultisig(String txDataHex) {
		Map<String, Object> params = new HashMap<>();
		params.put("tx_data_hex", txDataHex);
		Map<String, Object> response = rpcConnection.sendJsonRequest("finalize_multisig", params);
		log.debug("response => {}", response);
		
		return JsonUtils.deserialize(MoneroRpcConnection.DEFAULT_MAPPER, JsonUtils.serialize(MoneroRpcConnection.DEFAULT_MAPPER, response.get("result")), String.class);
	}

	public static final String generatePaymentId() {
		String f32 = UUID.randomUUID().toString().replace("-", "");
		String l32 = UUID.randomUUID().toString().replace("-", "");
		return f32.concat(l32);		
	}
	
	public void closeWallet() {
		Map<String, Object> response = rpcConnection.sendJsonRequest("close_wallet");
		log.debug("response => {}", response);
	}

	public void openWallet(String walletFileName, String password) {
		Map<String, Object> params = new HashMap<>();
		params.put("filename", walletFileName);
		params.put("password", password);
		Map<String, Object> response = rpcConnection.sendJsonRequest("open_wallet", params);
		log.debug("response => {}", response);
	}

}
