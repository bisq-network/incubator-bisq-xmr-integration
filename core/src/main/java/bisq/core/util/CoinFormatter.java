package bisq.core.util;

import org.bitcoinj.core.Monetary;

public interface CoinFormatter {
	
	/**
	 * 
	 * @param coin
	 * @return
	 */
	String formatCoin(Monetary coin);
	
	/**
	 * 
	 * @param coin
	 * @return
	 */
	String formatCoinWithCode(Monetary coin);
	
	/**
	 * 
	 * @param value
	 * @return
	 */
	String formatCoinWithCode(long value);

}
