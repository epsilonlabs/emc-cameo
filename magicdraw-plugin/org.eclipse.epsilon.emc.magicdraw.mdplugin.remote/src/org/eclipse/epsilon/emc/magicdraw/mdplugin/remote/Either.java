/*******************************************************************************
 * Copyright (c) 2022 Holger, University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Holger (StackOverflow) - initial API and implementation
 *    Antonio Garcia-Dominguez - adaptation, add flatMapRight
 *******************************************************************************/
package org.eclipse.epsilon.emc.magicdraw.mdplugin.remote;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Either type, similar to Scala's. Taken from <a href="https://stackoverflow.com/questions/26162407">this StackOverflow thread</a>.
 */
public abstract class Either<L, R> {
	public static <L, R> Either<L, R> left(L value) {
		return new Either<L, R>() {
			@Override
			public <T> T map(Function<? super L, ? extends T> lFunc, Function<? super R, ? extends T> rFunc) {
				return lFunc.apply(value);
			}
		};
	}

	public static <L, R> Either<L, R> right(R value) {
		return new Either<L, R>() {
			@Override
			public <T> T map(Function<? super L, ? extends T> lFunc, Function<? super R, ? extends T> rFunc) {
				return rFunc.apply(value);
			}

		};
	}

	private Either() {}

	public abstract <T> T map(Function<? super L, ? extends T> lFunc, Function<? super R, ? extends T> rFunc);

	@SuppressWarnings("unchecked")
	public <T> Either<T, R> mapLeft(Function<? super L, ? extends T> lFunc) {
		return this.<Either<T, R>>map(t -> left(lFunc.apply(t)), t -> (Either<T, R>) this);
	}

	@SuppressWarnings("unchecked")
	public <T> Either<L, T> mapRight(Function<? super R, ? extends T> lFunc) {
		return this.<Either<L, T>>map(t -> (Either<L, T>) this, t -> right(lFunc.apply(t)));
	}

	/**
	 * Added method to avoid chaining of Eithers (keeping the same left type).
	 */
	@SuppressWarnings("unchecked")
	public <T> Either<L, T> flatMapRight(Function<? super R, Either<L, T>> lFunc) {
		return this.<Either<L, T>>map(t -> (Either<L, T>) this, t -> lFunc.apply(t));
	}
	
	public void apply(Consumer<? super L> lFunc, Consumer<? super R> rFunc) {
		map(consume(lFunc), consume(rFunc));
	}

	private <T> Function<T, Void> consume(Consumer<T> c) {
		return t -> {
			c.accept(t);
			return null;
		};
	}
}