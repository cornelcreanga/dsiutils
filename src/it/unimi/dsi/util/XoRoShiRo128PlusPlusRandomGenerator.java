/*
 * DSI utilities
 *
 * Copyright (C) 2013-2022 Sebastiano Vigna
 *
 * This program and the accompanying materials are made available under the
 * terms of the GNU Lesser General Public License v2.1 or later,
 * which is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html,
 * or the Apache Software License 2.0, which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later OR Apache-2.0
 */

package it.unimi.dsi.util;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Random;
import java.util.SplittableRandom;

import org.apache.commons.math3.random.AbstractRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;

import it.unimi.dsi.Util;
import it.unimi.dsi.logging.ProgressLogger;

/**
 * A fast, all-purpose, rock-solid, small-state {@linkplain RandomGenerator pseudorandom number
 * generator}. It has excellent speed, but its state space (128 bits) that is large enough for mild
 * parallelism only; it passes all tests we are aware of. In Java, it is slightly faster than a
 * {@link XoRoShiRo128StarStarRandomGenerator}. More information can be found at our
 * <a href="http://prng.di.unimi.it/">PRNG page</a>.
 *
 * <p>
 * Note that starting with Java 17 you can find this generator in <a href=
 * "https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/package-summary.html"><code>java.util.random</code></a>.
 *
 * <p>
 * If you need to generate just floating-point numbers, {@link XoRoShiRo128PlusRandomGenerator} is
 * slightly faster. If you can use more space, you might try
 * {@link XoShiRo256PlusPlusRandomGenerator}.
 *
 * <p>
 * By using the supplied {@link #jump()} method it is possible to generate non-overlapping long
 * sequences for parallel computations; {@link #longJump()} makes it possible to create several
 * starting points, each providing several non-overlapping sequences, for distributed computations.
 * This class provides also a {@link #split()} method to support recursive parallel computations, in
 * the spirit of {@link SplittableRandom}.
 *
 * <p>
 * <strong>Warning</strong>: before release 2.6.3, the {@link #split()} method would not alter the
 * state of the caller, and it would return instances initialized in the same way if called multiple
 * times. This was a major mistake in the implementation and it has been fixed, but as a consequence
 * the output of the caller after a call to {@link #split()} is now different, and the result of
 * {@link #split()} is initialized in a different way.
 *
 * <p>
 * Note that this is not a {@linkplain SecureRandom secure generator}.
 *
 * @version 1.0
 * @see it.unimi.dsi.util
 * @see Random
 * @see XoRoShiRo128PlusPlusRandom
 */

public class XoRoShiRo128PlusPlusRandomGenerator extends AbstractRandomGenerator implements Serializable {
	private static final long serialVersionUID = 0L;
	/** The internal state of the algorithm. */
	private long s0, s1;

	protected XoRoShiRo128PlusPlusRandomGenerator(final long s0, final long s1) {
		this.s0 = s0;
		this.s1 = s1;
	}

	/** Creates a new generator seeded using {@link Util#randomSeed()}. */
	public XoRoShiRo128PlusPlusRandomGenerator() {
		this(Util.randomSeed());
	}

	/** Creates a new generator using a given seed.
	 *
	 * @param seed a seed for the generator.
	 */
	public XoRoShiRo128PlusPlusRandomGenerator(final long seed) {
		setSeed(seed);
	}

	/** Returns a copy of this generator. The sequences produced by this generator and by the returned generator will be identical.
	 *
	 * <p>This method is particularly useful in conjunction with the {@link #jump()} (or {@link #longJump()}) method: by calling repeatedly
	 * {@link #jump() jump().copy()} over a generator it is possible to create several generators producing non-overlapping sequences.
	 *
	 * @return a copy of this generator.
	 */
	public XoRoShiRo128PlusPlusRandomGenerator copy() {
		return new XoRoShiRo128PlusPlusRandomGenerator(s0, s1);
	}


	@Override
	public long nextLong() {
		final long s0 = this.s0;
		long s1 = this.s1;

		final long result = Long.rotateLeft(s0 + s1, 17) + s0;

		s1 ^= s0;
		this.s0 = Long.rotateLeft(s0, 49) ^ s1 ^ s1 << 21;
		this.s1 = Long.rotateLeft(s1, 28);
		return result;
	}

	@Override
	public int nextInt() {
		return (int)(nextLong());
	}

	@Override
	public int nextInt(final int n) {
		return (int)nextLong(n);
	}

	/** Returns a pseudorandom uniformly distributed {@code long} value
	 * between 0 (inclusive) and the specified value (exclusive), drawn from
	 * this random number generator's sequence. The algorithm used to generate
	 * the value guarantees that the result is uniform, provided that the
	 * sequence of 64-bit values produced by this generator is.
	 *
	 * @param n the positive bound on the random number to be returned.
	 * @return the next pseudorandom {@code long} value between {@code 0} (inclusive) and {@code n} (exclusive).
	 */
	public long nextLong(final long n) {
		if (n <= 0) throw new IllegalArgumentException("illegal bound " + n + " (must be positive)");
		long t = nextLong();
		final long nMinus1 = n - 1;
		// Rejection-based algorithm to get uniform integers in the general case
		for (long u = t >>> 1; u + nMinus1 - (t = u % n) < 0; u = nextLong() >>> 1);
		return t;
	}

	@Override
	public double nextDouble() {
		return (nextLong() >>> 11) * 0x1.0p-53;
	}

	/**
	 * Returns the next pseudorandom, uniformly distributed
	 * {@code double} value between {@code 0.0} and
	 * {@code 1.0} from this random number generator's sequence,
	 * using a fast multiplication-free method which, however,
	 * can provide only 52 significant bits.
	 *
	 * <p>This method is faster than {@link #nextDouble()}, but it
	 * can return only dyadic rationals of the form <var>k</var> / 2<sup>&minus;52</sup>,
	 * instead of the standard <var>k</var> / 2<sup>&minus;53</sup>. Before
	 * version 2.4.1, this was actually the standard implementation of
	 * {@link #nextDouble()}, so you can use this method if you need to
	 * reproduce exactly results obtained using previous versions.
	 *
	 * <p>The only difference between the output of this method and that of
	 * {@link #nextDouble()} is an additional least significant bit set in half of the
	 * returned values. For most applications, this difference is negligible.
	 *
	 * @return the next pseudorandom, uniformly distributed {@code double}
	 * value between {@code 0.0} and {@code 1.0} from this
	 * random number generator's sequence, using 52 significant bits only.
	 *
	 * @since 2.4.1
	 */
	public double nextDoubleFast() {
		return Double.longBitsToDouble(0x3FFL << 52 | nextLong() >>> 12) - 1.0;
	}

	@Override
	public float nextFloat() {
		return (nextLong() >>> 40) * 0x1.0p-24f;
	}

	@Override
	public boolean nextBoolean() {
		return nextLong() < 0;
	}

	@Override
	public void nextBytes(final byte[] bytes) {
		int i = bytes.length, n = 0;
		while(i != 0) {
			n = Math.min(i, 8);
			for (long bits = nextLong(); n-- != 0; bits >>= 8) bytes[--i] = (byte)bits;
		}
	}

	protected XoRoShiRo128PlusPlusRandomGenerator jump(final long[] jump) {
		long s0 = 0;
		long s1 = 0;
		for (final long element : jump)
			for(int b = 0; b < 64; b++) {
				if ((element & 1L << b) != 0) {
					s0 ^= this.s0;
					s1 ^= this.s1;
				}
				nextLong();
			}

		this.s0 = s0;
		this.s1 = s1;
		return this;
	}


	private static final long[] JUMP = { 0x2bd7a6a6e99c2ddcL, 0x0992ccaf6a6fca05L };

	/** The jump function for this generator. It is equivalent to 2<sup>64</sup>
	 * calls to {@link #nextLong()}; it can be used to generate 2<sup>64</sup>
	 * non-overlapping subsequences for parallel computations.
	 *
	 * @return this generator.
	 * @see #copy()
	 */

	public XoRoShiRo128PlusPlusRandomGenerator jump() {
		return jump(JUMP);
	}

	private static final long[] LONG_JUMP = { 0x360fd5f2cf8d5d99L, 0x9c6e6877736c46e3L };

	/** The long-jump function for this generator. It is equivalent to 2<sup>96</sup>
	 * calls to {@link #nextLong()}; it can be used to generate 2<sup>32</sup> starting points,
	 * from each of which {@link #jump()} will generate 2<sup>32</sup> non-overlapping
	 * subsequences for parallel distributed computations.
	 *
	 * @return this generator.
	 * @see #copy()
	 */

	public XoRoShiRo128PlusPlusRandomGenerator longJump() {
		return jump(LONG_JUMP);
	}



	/**
	 * Returns a new instance that shares no mutable state
	 * with this instance. The sequence generated by the new instance
	 * depends deterministically from the state of this instance,
	 * but the probability that the sequence generated by this
	 * instance and by the new instance overlap is negligible.
	 *
	 * <p><strong>Warning</strong>: before release 2.6.3, this method
	 * would not alter the state of the caller, and it would return instances initialized in the same
	 * way if called multiple times. This was a major mistake in the implementation and it has been fixed,
	 * but as a consequence the output of this instance after a call to this method is
	 * now different, and the returned instance is initialized in a different way.
	 *
	 * @return the new instance.
	 */
	public XoRoShiRo128PlusPlusRandomGenerator split() {
		nextLong();
		final XoRoShiRo128PlusPlusRandomGenerator split = copy();

		long h0 = s0;
		long h1 = s1;
		long h2 = s0 + 0x55a650a4c1dac3e9L; // Random constants
		long h3 = s1 + 0xb39ae98dfa439b73L;

		// A round of SpookyHash ShortMix
		h2 = Long.rotateLeft(h2, 50);
		h2 += h3;
		h0 ^= h2;
		h3 = Long.rotateLeft(h3, 52);
		h3 += h0;
		h1 ^= h3;
		h0 = Long.rotateLeft(h0, 30);
		h0 += h1;
		h2 ^= h0;
		h1 = Long.rotateLeft(h1, 41);
		h1 += h2;
		h3 ^= h1;
		h2 = Long.rotateLeft(h2, 54);
		h2 += h3;
		h0 ^= h2;
		h3 = Long.rotateLeft(h3, 48);
		h3 += h0;
		h1 ^= h3;
		h0 = Long.rotateLeft(h0, 38);
		h0 += h1;
		h2 ^= h0;
		h1 = Long.rotateLeft(h1, 37);
		h1 += h2;
		h3 ^= h1;
		h2 = Long.rotateLeft(h2, 62);
		h2 += h3;
		h0 ^= h2;
		h3 = Long.rotateLeft(h3, 34);
		h3 += h0;
		h1 ^= h3;
		h0 = Long.rotateLeft(h0, 5);
		h0 += h1;
		h2 ^= h0;
		h1 = Long.rotateLeft(h1, 36);
		h1 += h2;
		//h3 ^= h1;

		split.s0 = h0;
		split.s1 = h1;

		return split;
	}

	/** Sets the seed of this generator.
	 *
	 * <p>The argument will be used to seed a {@link SplitMix64RandomGenerator}, whose output
	 * will in turn be used to seed this generator. This approach makes &ldquo;warmup&rdquo; unnecessary,
	 * and makes the probability of starting from a state
	 * with a large fraction of bits set to zero astronomically small.
	 *
	 * @param seed a seed for this generator.
	 */
	@Override
	public void setSeed(final long seed) {
		final SplitMix64RandomGenerator r = new SplitMix64RandomGenerator(seed);
		s0 = r.nextLong();
		s1 = r.nextLong();
	}


	/** Sets the state of this generator.
	 *
	 * <p>The internal state of the generator will be reset, and the state array filled with the provided array.
	 *
	 * @param state an array of 2 longs; at least one must be nonzero.
	 */
	public void setState(final long[] state) {
		if (state.length != 2) throw new IllegalArgumentException("The argument array contains " + state.length + " longs instead of " + 2);
		s0 = state[0];
		s1 = state[1];
	}

	public static void main(final String[] arg) {
		final long n = Long.parseLong(arg[0]);
		long x = 0;
		final ProgressLogger pl = new ProgressLogger();
		final XoRoShiRo128PlusPlusRandomGenerator r = new XoRoShiRo128PlusPlusRandomGenerator(0);
		for(int k = 10; k-- != 0;) {
			pl.start("Measuring...");
			for (long i = n; i-- != 0;)
				x ^= r.nextLong();
			pl.done(n);
			if (x == 0) System.out.println(x);
		}
	}
}
