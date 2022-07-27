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

import org.eclipse.epsilon.emc.magicdraw.modelapi.ListGetRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ListPositionValue;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ProxyList;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;

/**
 * This is a proxy for an EList inside MagicDraw. All list operations are
 * delegated to the running MagicDraw instance.
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
		Value value = model.client.listGet(ListGetRequest.newBuilder()
			.setList(proxyList)
			.setPosition(index)
			.build());

		return model.getPropertyGetter().decodeValue(value);
	}

	@Override
	public Object set(int index, Object element) {
		Value value = model.encoder.encode(element);
		Value oldValue = model.client.listSet(createListPositionValue(index, value));

		return model.getPropertyGetter().decodeValue(oldValue);
	}

	@Override
	public void add(int index, Object element) {
		Value value = model.encoder.encode(element);
		model.client.listAdd(createListPositionValue(index, value));
	}

	@Override
	public Object remove(int index) {
		// TODO Auto-generated method stub
		return super.remove(index);
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
		// TODO
	}

	public Object move(int newPosition, int oldPosition) {
		// TODO
		return null;
	}

}