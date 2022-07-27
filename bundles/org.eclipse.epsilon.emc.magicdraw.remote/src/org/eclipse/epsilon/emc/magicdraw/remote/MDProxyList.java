/*******************************************************************************
 * Copyright (c) 2022 University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Antonio Garcia-Dominguez - initial API and implementation
 *******************************************************************************/
package org.eclipse.epsilon.emc.magicdraw.remote;

import java.util.AbstractList;

import org.eclipse.epsilon.emc.magicdraw.modelapi.ListPosition;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ListPositionValue;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ProxyList;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;

/**
 * This is a proxy for an EList inside MagicDraw. All core list operations
 * are delegated to the running MagicDraw instance.
 */
public class MDProxyList extends AbstractList<Object> {

	private final MagicDrawModel model;
	private final ProxyList proxyList;

	public MDProxyList(MagicDrawModel model, ProxyList proxyList) {
		this.model = model;
		this.proxyList = proxyList;
	}

	@Override
	public int size() {
		return model.client.listSize(proxyList).getValue();
	}

	@Override
	public Object get(int index) {
		Value value = model.client.listGet(createListPosition(index));
		return model.getPropertyGetter().decodeValue(value);
	}

	@Override
	public Object set(int index, Object element) {
		model.ensureSessionOpened();
		Value value = model.encoder.encode(element);
		Value oldValue = model.client.listSet(createListPositionValue(index, value));
		return model.getPropertyGetter().decodeValue(oldValue);
	}

	@Override
	public void add(int index, Object element) {
		model.ensureSessionOpened();
		Value value = model.encoder.encode(element);
		model.client.listAdd(createListPositionValue(index, value));
	}

	@Override
	public Object remove(int index) {
		model.ensureSessionOpened();
		Value oldValue = model.client.listRemove(createListPosition(index));
		return model.getPropertyGetter().decodeValue(oldValue);
	}

	private ListPosition createListPosition(int index) {
		return ListPosition.newBuilder()
				.setList(proxyList).setPosition(index).build();
	}

	private ListPositionValue createListPositionValue(int index, Value value) {
		return ListPositionValue.newBuilder()
			.setList(proxyList)
			.setPosition(index)
			.setValue(value)
			.build();
	}

	/* EList-inspired operations */
	
	public void move(int newPosition, Object e) {
		model.ensureSessionOpened();
		final Value value = model.encoder.encode(e);
		model.client.listMoveObject(createListPositionValue(newPosition, value));
	}

	/*
	 * NOTE:
	 *
	 * <code>public Object move(int newPosition, int oldPosition)</code>
	 *
	 * is not implemented yet as it is inaccessible from Epsilon. Epsilon's
	 * reflective lookup confuses it with the {@link move(int, Object)} version.
	 */

}