package bisq.core.xmr;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import com.google.common.math.LongMath;
import com.google.common.primitives.Longs;

import bisq.core.util.XmrMonetaryFormat;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Cloned from org.bitcoinj.core.Coin class and altered SMALLEST_UNIT_EXPONENT as Fiat is final.
 * <p/>
 * Represents a monetary Monero value. It was decided to not fold this into {@link org.bitcoinj.core.Coin} because of type
 * safety. Volume values always come with an attached currency code.
 * <p/>
 * This class is immutable.
 */

/**
 * Represents a monetary Monero value. This class is immutable.
 */
@Slf4j
public final class XmrCoin implements Monetary, Comparable<XmrCoin>, Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
     * Number of decimals for one Monero. This constant is useful for quick adapting to other coins because a lot of
     * constants derive from it.
     */
    public static final int SMALLEST_UNIT_EXPONENT = 12;
    
    public static MathContext MATH_CONTEXT = new MathContext(SMALLEST_UNIT_EXPONENT, RoundingMode.DOWN);

    /**
     * The number of satoshis equal to one Monero.
     */
    private static final long COIN_VALUE = LongMath.pow(10, SMALLEST_UNIT_EXPONENT);

    /**
     * Zero Moneros.
     */
    public static final XmrCoin ZERO = XmrCoin.valueOf(0);

    /**
     * One Monero.
     */
    public static final XmrCoin COIN = XmrCoin.valueOf(COIN_VALUE);

    /**
     * 0.01 Moneros. This unit is not really used much.
     */
    public static final XmrCoin CENT = COIN.divide(100);

    /**
     * 0.001 Moneros, also known as 1 mBTC.
     */
    public static final XmrCoin MILLICOIN = COIN.divide(1000);

    /**
     * 0.000001 Moneros, also known as 1 µBTC or 1 uBTC.
     */
    public static final XmrCoin MICROCOIN = MILLICOIN.divide(1000);

    /**
     * A satoshi is the smallest unit that can be transferred. 100 million of them fit into a Monero.
     */
    public static final XmrCoin SATOSHI = XmrCoin.valueOf(1);

    public static final XmrCoin FIFTY_COINS = COIN.multiply(50);

    /**
     * Represents a monetary value of minus one satoshi.
     */
    public static final XmrCoin NEGATIVE_SATOSHI = XmrCoin.valueOf(-1);
    
    /**
     * Dust value - any amount < 0.01 XMR 
     */
    public static final XmrCoin DUST = XmrCoin.valueOf(9999999999l);

    /**
     * The number of satoshis of this monetary value.
     */
    public final long value;

    private XmrCoin(final long satoshis) {
        this.value = satoshis;
    }

    public static XmrCoin valueOf(final long satoshis) {
        return new XmrCoin(satoshis);
    }

    @Override
    public int smallestUnitExponent() {
        return SMALLEST_UNIT_EXPONENT;
    }

    /**
     * Returns the number of satoshis of this monetary value.
     */
    @Override
    public long getValue() {
        return value;
    }

    /**
     * Convert an amount expressed in the way humans are used to into satoshis.
     */
    public static XmrCoin valueOf(final int coins, final int cents) {
        checkArgument(cents < 100);
        checkArgument(cents >= 0);
        checkArgument(coins >= 0);
        final XmrCoin coin = COIN.multiply(coins).add(CENT.multiply(cents));
        return coin;
    }

    /**
     * Parses an amount expressed in the way humans are used to.<p>
     * <p/>
     * This takes string in a format understood by {@link BigDecimal#BigDecimal(String)},
     * for example "0", "1", "0.10", "1.23E3", "1234.5E-5".
     *
     * @throws IllegalArgumentException if you try to specify fractional satoshis, or a value out of range.
     */
    //Careful with calls that may cause recursion
    public static XmrCoin parseCoin(final String str) throws Exception {
        try {
            long satoshis = new BigDecimal(str).movePointRight(SMALLEST_UNIT_EXPONENT).round(new MathContext(str.length() + SMALLEST_UNIT_EXPONENT, RoundingMode.DOWN)).longValue();
            return XmrCoin.valueOf(satoshis);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(e); // Repackage exception to honor method contract
        }
    }

    public XmrCoin add(final XmrCoin value) {
        return new XmrCoin(LongMath.checkedAdd(this.value, value.value));
    }

    /** Alias for add */
    public XmrCoin plus(final XmrCoin value) {
        return add(value);
    }

    public XmrCoin subtract(final XmrCoin value) {
        return new XmrCoin(LongMath.checkedSubtract(this.value, value.value));
    }

    /** Alias for subtract */
    public XmrCoin minus(final XmrCoin value) {
        return subtract(value);
    }

    public XmrCoin multiply(final long factor) {
        return new XmrCoin(LongMath.checkedMultiply(this.value, factor));
    }

    /** Alias for multiply */
    public XmrCoin times(final long factor) {
        return multiply(factor);
    }

    /** Alias for multiply */
    public XmrCoin times(final int factor) {
        return multiply(factor);
    }

    public XmrCoin divide(final long divisor) {
        return new XmrCoin(this.value / divisor);
    }

    /** Alias for divide */
    public XmrCoin div(final long divisor) {
        return divide(divisor);
    }

    /** Alias for divide */
    public XmrCoin div(final int divisor) {
        return divide(divisor);
    }

    public XmrCoin[] divideAndRemainder(final long divisor) {
        return new XmrCoin[] { new XmrCoin(this.value / divisor), new XmrCoin(this.value % divisor) };
    }

    public long divide(final XmrCoin divisor) {
        return this.value / divisor.value;
    }

    /**
     * Returns true if and only if this instance represents a monetary value greater than zero,
     * otherwise false.
     */
    public boolean isPositive() {
        return signum() == 1;
    }

    /**
     * Returns true if and only if this instance represents a monetary value less than zero,
     * otherwise false.
     */
    public boolean isNegative() {
        return signum() == -1;
    }

    /**
     * Returns true if and only if this instance represents zero monetary value,
     * otherwise false.
     */
    public boolean isZero() {
        return signum() == 0;
    }

    /**
     * Returns true if the monetary value represented by this instance is greater than that
     * of the given other XmrCoin, otherwise false.
     */
    public boolean isGreaterThan(XmrCoin other) {
        return compareTo(other) > 0;
    }

    /**
     * Returns true if the monetary value represented by this instance is less than that
     * of the given other XmrCoin, otherwise false.
     */
    public boolean isLessThan(XmrCoin other) {
        return compareTo(other) < 0;
    }

    public XmrCoin shiftLeft(final int n) {
        return new XmrCoin(this.value << n);
    }

    public XmrCoin shiftRight(final int n) {
        return new XmrCoin(this.value >> n);
    }

    @Override
    public int signum() {
        if (this.value == 0)
            return 0;
        return this.value < 0 ? -1 : 1;
    }

    public XmrCoin negate() {
        return new XmrCoin(-this.value);
    }

    /**
     * Returns the number of satoshis of this monetary value. It's deprecated in favour of accessing {@link #value}
     * directly.
     */
    public long longValue() {
        return this.value;
    }

    private static final XmrMonetaryFormat FRIENDLY_FORMAT = XmrMonetaryFormat.XMR.minDecimals(2).repeatOptionalDecimals(1, 10).postfixCode();

    private static final XmrMonetaryFormat FRIENDLY_FORMAT_NO_CODE = XmrMonetaryFormat.XMR.minDecimals(2).repeatOptionalDecimals(1, 10).noCode();

    /**
     * Returns the value as a 0.12 type string. More digits after the decimal place will be used
     * if necessary, but two will always be present.
     */
    public String toFriendlyString() {
        return FRIENDLY_FORMAT.format(this).toString();
    }

    public String toFriendlyStringNoCode() {
        return FRIENDLY_FORMAT_NO_CODE.format(this).toString();
    }

    private static final XmrMonetaryFormat PLAIN_FORMAT = XmrMonetaryFormat.XMR.minDecimals(0).repeatOptionalDecimals(1, 12).noCode();

    /**
     * <p>
     * Returns the value as a plain string denominated in BTC.
     * The result is unformatted with no trailing zeroes.
     * For instance, a value of 150000 satoshis gives an output string of "0.0015" BTC
     * </p>
     */
    public String toPlainString() {
        return PLAIN_FORMAT.format(this).toString();
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return this.value == ((XmrCoin)o).value;
    }

    @Override
    public int hashCode() {
        return (int) this.value;
    }

    @Override
    public int compareTo(final XmrCoin other) {
        return Longs.compare(this.value, other.value);
    }
    
    private static BigDecimal obtainRateAsBigDecimal(String xmrConversionRateAsString) {
    	BigDecimal rate = BigDecimal.ONE;
    	try {
        	rate = rate.divide(new BigDecimal(xmrConversionRateAsString), MATH_CONTEXT);
    	} catch (Exception e) {
    		log.error("Exception occurred: {}", xmrConversionRateAsString);
		}
    	
    	return rate;
    }
    /**
     * 
     * @param coin
     * @param currencyCode
     * @param xmrConversionRateAsString
     * @return
     */
    public static XmrCoin fromCoin2XmrCoin(Coin coin, String currencyCode, String xmrConversionRateAsString) {
    	BigDecimal rate = obtainRateAsBigDecimal(xmrConversionRateAsString);
    	coin = coin != null ? coin : Coin.ZERO;
    	BigDecimal coinBigDecimal = new BigDecimal(coin.getValue());
    	BigDecimal xmrCoinBigDecimal = coinBigDecimal.multiply(rate, MATH_CONTEXT);
    	//TODO(niyid) Handle conversion for BSQ with factor multiplier
    	BigDecimal bsqFactor = "BSQ".equals(currencyCode) ? new BigDecimal(1_000_000) : BigDecimal.ONE;//For BSQ, the scale/precision must be adjusted
    	BigDecimal rounded = xmrCoinBigDecimal.multiply(bsqFactor).multiply(new BigDecimal(10_000)).round(new MathContext(SMALLEST_UNIT_EXPONENT, RoundingMode.DOWN));
    	
    	return XmrCoin.valueOf(rounded.longValue());
    }
    
    /**
     * 
     * @param coin
     * @param currencyCode
     * @param xmrConversionRateAsString
     * @return
     */
    public static Coin fromXmrCoin2Coin(XmrCoin coin, String currencyCode, String xmrConversionRateAsString) {
    	BigDecimal rate = obtainRateAsBigDecimal(xmrConversionRateAsString);
    	coin = coin != null ? coin : XmrCoin.ZERO;
    	BigDecimal xmrCoinBigDecimal = new BigDecimal(coin.getValue());
    	BigDecimal coinBigDecimal = xmrCoinBigDecimal.divide(rate, MATH_CONTEXT);
    	BigDecimal bsqFactor = "BSQ".equals(currencyCode) ? new BigDecimal(0.000_001) : BigDecimal.ONE;//For BSQ, the scale/precision must be adjusted
    	BigDecimal rounded = coinBigDecimal.multiply(bsqFactor).multiply(new BigDecimal(0.0_001)).round(new MathContext(Coin.SMALLEST_UNIT_EXPONENT, RoundingMode.DOWN));
    	return Coin.valueOf(rounded.longValue());
    }
}
