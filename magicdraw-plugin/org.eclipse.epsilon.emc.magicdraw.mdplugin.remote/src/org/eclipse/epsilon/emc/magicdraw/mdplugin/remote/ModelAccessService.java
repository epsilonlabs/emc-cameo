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
package org.eclipse.epsilon.emc.magicdraw.mdplugin.remote;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.epsilon.emc.magicdraw.modelapi.AllOfRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.BooleanCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.CreateInstanceRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.DoubleCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Empty;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetElementByIDRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetEnumerationValueRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetEnumerationValueResponse;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetFeatureValueRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetTypeRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.IntegerCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementType;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementTypeReference;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceConstants;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceGrpc;
import org.eclipse.epsilon.emc.magicdraw.modelapi.OpenSessionRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.StringCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.rpc.ErrorInfo;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.foundation.MDObject;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.uml.BaseElement;
import com.nomagic.magicdraw.uml.Finder;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.impl.ElementsFactory;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.StreamObserver;

public class ModelAccessService extends ModelServiceGrpc.ModelServiceImplBase {
	private static final Logger LOGGER = LoggerFactory.getLogger(ModelAccessService.class);
	private static final String GRPC_DOMAIN = ModelAccessService.class.getPackage().getName();

	@Override
	public void allOf(AllOfRequest request, StreamObserver<ModelElementCollection> responseObserver) {
		Project project = Application.getInstance().getProject();
		if (project == null) {
			replyProjectNotOpen(responseObserver);
			return;
		}

		EClassifier eClassifier;
		if (request.getTypeName() != null && request.getTypeName().length() > 0) {
			Collection<EClassifier> options = findEClassifier(request.getTypeName());
			checkForAmbiguousType(request.getTypeName(), responseObserver, options);
			if (options.isEmpty()) return;
			eClassifier = options.iterator().next();
		} else {
			eClassifier = null;
		}

		EObject root;
		final String rootElementHyperlink = request.getRootElementHyperlink();
		if (rootElementHyperlink == null || rootElementHyperlink.length() == 0) {
			root = project.getPrimaryModel(); 
		} else {
			BaseElement element = Finder.byHyperlink().find(project, rootElementHyperlink);
			if (element == null) {
				responseObserver.onError(Status.INVALID_ARGUMENT
					.withDescription(String.format("Could not find element with URI %s", rootElementHyperlink))
					.asRuntimeException());
				return;
			} else if (!(element instanceof EObject)) {
				responseObserver.onError(Status.INVALID_ARGUMENT
					.withDescription(String.format("Element with URI %s is not an EObject", rootElementHyperlink))
					.asRuntimeException());
				return;
			} else {
				root = (EObject) element;
			}
		}

		final ModelElementCollection allOfResponse = getAllOf(eClassifier, root, request.getOnlyExactType());
		responseObserver.onNext(allOfResponse);
		responseObserver.onCompleted();
	}

	private void checkForAmbiguousType(String type, StreamObserver<?> responseObserver, Collection<? extends EClassifier> options) {
		if (options.size() == 0) {
			replyTypeNotFound(responseObserver, type);
		} else if (options.size() > 1) {
			List<String> nsURIs = options.stream().map(e -> getFullyQualifiedName(e) + " from " + e.getEPackage()).collect(Collectors.toList());
			LOGGER.warn(String.format("Type '%s' is ambiguous (options: %s)", type, nsURIs));
		}
	}

	private ModelElementCollection getAllOf(EClassifier eClassifier, EObject root, final boolean onlyExactType) {
		final ModelElementCollection.Builder builder = ModelElementCollection.newBuilder();
		final TreeIterator<EObject> it = EcoreUtil.getAllProperContents(root, true);

		Predicate<EObject> pred;
		if (eClassifier == null) {
			pred = (eob) -> eob instanceof MDObject;
		}
		else if (onlyExactType) {
			pred = (eob) -> eob instanceof MDObject && eob.eClass() == eClassifier;
		}
		else {
			pred = (eob) -> eob instanceof MDObject && eClassifier.isInstance(eob);
		}

		while (it.hasNext()) {
			EObject eob = it.next();
			if (pred.test(eob)) {
				builder.addValues(encodeModelElement((MDObject) eob));
			}
		}

		return builder.build();
	}

	private ModelElement encodeModelElement(MDObject eob) {
		return ModelElement.newBuilder()
				.setElementID(eob.getID())
				.setMetamodelUri(eob.eClass().getEPackage().getNsURI())
				.setTypeName(getFullyQualifiedName(eob.eClass()))
				.build();
	}

	private String getFullyQualifiedName(EClassifier eClass) {
		List<String> parts = new ArrayList<>();
		parts.add(eClass.getName());
		for (EPackage pkg = eClass.getEPackage(); pkg != null; pkg = pkg.getESuperPackage()) {
			parts.add(pkg.getName());
		}

		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (ListIterator<String> it = parts.listIterator(parts.size()); it.hasPrevious(); ) {
			if (first) {
				first = false;
			} else {
				sb.append("::");
			}
			sb.append(it.previous());
		}

		return sb.toString();
	}

	private Collection<EClassifier> findEClassifier(String typeName) {
		final List<String> parts = Arrays.asList(typeName.split("::"));
		if (parts.size() > 1) {
			final EClassifier eClassifier = findEClassifierAbsolute(parts, getRootEPackages());
			if (eClassifier == null) {
				LOGGER.warn(String.format("Cannot find type '%s'", typeName));
				return Collections.emptySet();
			}
			return Collections.singleton(eClassifier);
		} else {
			final Set<EClassifier> options = new HashSet<>();
			findEClassifierByName(parts.get(0), getRootEPackages(), options);
			return options;
		}
	}

	private void findEClassifierByName(String name, Iterable<EPackage> scope, Collection<EClassifier> options) {
		for (EPackage pkg : scope) {
			EClassifier eClassifier = pkg.getEClassifier(name);
			if (eClassifier != null) {
				options.add(eClassifier);
			}
			findEClassifierByName(name, pkg.getESubpackages(), options);
		}
	}

	private Iterable<EPackage> getRootEPackages() {
		return () -> EPackage.Registry.INSTANCE.values()
			.stream()
			.filter(pkg -> (pkg instanceof EPackage))
			.map(pkg -> (EPackage) pkg).iterator();
	}

	private EClassifier findEClassifierAbsolute(List<String> parts, Iterable<EPackage> scope) {
		assert parts.size() > 1;

		for (EPackage pkg : scope) {
			if (parts.get(0).equals(pkg.getName())) {
				if (parts.size() == 2) {
					// Second part is just the classifier name
					EClassifier eClassifier = pkg.getEClassifier(parts.get(1));
					if (eClassifier != null) {
						return eClassifier;
					}
				} else {
					EClassifier eClassifier = findEClassifierAbsolute(
						parts.subList(1, parts.size()),
						pkg.getESubpackages()
					);
					if (eClassifier != null) {
						return eClassifier;
					}
				}
			}
		}

		return null;
	}

	@Override
	public void getFeatureValue(GetFeatureValueRequest request, StreamObserver<Value> responseObserver) {
		Project project = Application.getInstance().getProject();
		if (project == null) {
			replyProjectNotOpen(responseObserver);
			return;
		}

		final MDObject mdObject = (MDObject) project.getElementByID(request.getElementID());
		if (mdObject == null) {
			responseObserver.onError(Status.INVALID_ARGUMENT
				.withDescription(String.format("No object exists with ID '%s'", request.getElementID()))
				.asRuntimeException());
			return;
		}

		final Value.Builder vBuilder = Value.newBuilder();
		final EStructuralFeature eFeature = mdObject.eClass().getEStructuralFeature(request.getFeatureName());
		if (eFeature == null) {
			vBuilder.setNotDefined(true);
			LOGGER.warn(String.format("Feature '%s' is not defined for element type '%s'", request.getFeatureName(), getFullyQualifiedName(mdObject.eClass())));
		} else {
			final Object rawValue = mdObject.eGet(eFeature);
			if (rawValue != null) {
				if (eFeature instanceof EReference) {
					encodeReference((EReference) eFeature, rawValue, vBuilder);
				} else if (eFeature instanceof EAttribute) {
					encodeAttribute(eFeature, vBuilder, rawValue);
				} else {
					responseObserver.onError(Status.INVALID_ARGUMENT
						.withDescription(String.format("Unknown feature type '%s'", eFeature.eClass().getName()))
						.asRuntimeException());
				}
			}
		}

		responseObserver.onNext(vBuilder.build());
		responseObserver.onCompleted();
	}

	private void encodeAttribute(final EStructuralFeature eFeature, Value.Builder vBuilder, final Object rawValue) {
		if (eFeature.isMany()) {
			encodeManyScalars(vBuilder, rawValue);
		} else {
			encodeScalar(vBuilder, rawValue);
		}
	}

	private void encodeScalar(Value.Builder vBuilder, final Object rawValue) {
		if (rawValue instanceof Byte || rawValue instanceof Short || rawValue instanceof Integer || rawValue instanceof Long) {
			vBuilder.setLongValue((byte) rawValue);
		} else if (rawValue instanceof Float || rawValue instanceof Double) {
			vBuilder.setDoubleValue((float) rawValue);
		} else if (rawValue instanceof String) {
			vBuilder.setStringValue((String) rawValue);
		} else if (rawValue instanceof Boolean) {
			vBuilder.setBooleanValue((boolean) rawValue);
		}
	}

	private void encodeManyScalars(Value.Builder vBuilder, final Object rawValue) {
		final Iterator<?> it = ((Collection<?>) rawValue).iterator();
		final Object firstValue = it.next();
		if (rawValue instanceof Byte || rawValue instanceof Short || rawValue instanceof Integer || rawValue instanceof Long) {
			IntegerCollection.Builder iBuilder = IntegerCollection.newBuilder();
			iBuilder.addValues((long) firstValue);
			while (it.hasNext()) iBuilder.addValues((long) it.next());
			vBuilder.setLongValues(iBuilder.build());
		} else if (rawValue instanceof Float || rawValue instanceof Double) {
			DoubleCollection.Builder iBuilder = DoubleCollection.newBuilder();
			iBuilder.addValues((double) firstValue);
			while (it.hasNext()) iBuilder.addValues((double) it.next());
			vBuilder.setDoubleValues(iBuilder.build());
		} else if (rawValue instanceof String) {
			StringCollection.Builder iBuilder = StringCollection.newBuilder();
			iBuilder.addValues((String) firstValue);
			while (it.hasNext()) iBuilder.addValues((String) it.next());
			vBuilder.setStringValues(iBuilder.build());
		} else if (rawValue instanceof Boolean) {
			BooleanCollection.Builder iBuilder = BooleanCollection.newBuilder();
			iBuilder.addValues((boolean) firstValue);
			while (it.hasNext()) iBuilder.addValues((boolean) it.next());
			vBuilder.setBooleanValues(iBuilder.build());
		}
	}

	@SuppressWarnings("unchecked")
	private void encodeReference(EReference eReference, final Object rawValue, Value.Builder vBuilder) {
		if (eReference.isMany()) {
			ModelElementCollection.Builder cBuilder = ModelElementCollection.newBuilder();
			for (Iterator<MDObject> it = ((Iterable<MDObject>) rawValue).iterator(); it.hasNext(); ) {
				cBuilder.addValues(encodeModelElement(it.next()));
			}
			vBuilder.setReferenceValues(cBuilder.build());
		} else {
			vBuilder.setReferenceValue(encodeModelElement((MDObject) rawValue));
		}
	}

	@Override
	public void getType(GetTypeRequest request, StreamObserver<ModelElementType> responseObserver) {
		Collection<EClassifier> options = findEClassifier(request.getTypeName());
		checkForAmbiguousType(request.getTypeName(), responseObserver, options);
		if (options.isEmpty()) return;
		
		final EClassifier eClassifier = options.iterator().next();

		final ModelElementType.Builder builder = ModelElementType.newBuilder()
			.setMetamodelUri(eClassifier.getEPackage().getNsPrefix())
			.setTypeName(getFullyQualifiedName(eClassifier))
			.setIsAbstract(eClassifier instanceof EClass && ((EClass) eClassifier).isAbstract());

		if (eClassifier instanceof EClass) {
			for (EClass supertype : ((EClass) eClassifier).getEAllSuperTypes()) {
				builder.addAllSupertypes(ModelElementTypeReference.newBuilder()
					.setMetamodelUri(supertype.getEPackage().getNsURI())
					.setTypeName(getFullyQualifiedName(supertype)).build());
			}
		}

		responseObserver.onNext(builder.build());
		responseObserver.onCompleted();
	}

	@Override
	public void getEnumerationValue(GetEnumerationValueRequest request,
			StreamObserver<GetEnumerationValueResponse> responseObserver) {

		// Find the matching enumerations
		final Collection<EClassifier> eClassifierOptions = findEClassifier(request.getEnumeration());
		final List<EEnum> eEnumOptions = eClassifierOptions.stream()
			.filter(c -> (c instanceof EEnum))
			.map(c -> (EEnum) c)
			.collect(Collectors.toList());
		checkForAmbiguousType(request.getEnumeration(), responseObserver, eEnumOptions);
		if (eEnumOptions.isEmpty()) return;

		// Find the matching literal
		EEnumLiteral literal = null;
		for (EClassifier eClassifier : eEnumOptions) {
			if (eClassifier instanceof EEnum) {
				EEnum eEnum = (EEnum) eClassifier;
				literal = eEnum.getEEnumLiteral(request.getLabel());
				if (literal != null) {
					break;
				}
			}
		}

		// Report result
		if (literal == null) {
			responseObserver.onError(Status.INVALID_ARGUMENT
				.withDescription(String.format("Could not find enumeration value '%s' in '%s'", request.getLabel(), request.getEnumeration()))
				.asRuntimeException());
		} else {
			final GetEnumerationValueResponse response = GetEnumerationValueResponse.newBuilder()
				.setValue(literal.getValue())
				.setLiteral(literal.getLiteral())
				.setName(literal.getName())
				.build();

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		}
	}

	@Override
	public void getElementByID(GetElementByIDRequest request, StreamObserver<ModelElement> responseObserver) {
		Project project = Application.getInstance().getProject();
		if (project == null) {
			replyProjectNotOpen(responseObserver);
			return;
		}

		final String id = request.getElementID();
		final BaseElement element = project.getElementByID(id);
		if (element == null) {
			responseObserver.onError(Status.INVALID_ARGUMENT
				.withDescription(String.format("Could not find element with ID %s", id))
				.asRuntimeException());
			return;
		} else if (!(element instanceof MDObject)) {
			responseObserver.onError(Status.INVALID_ARGUMENT
					.withDescription(String.format("Element with ID %s is not an MDObject", id))
					.asRuntimeException());
			return;
		} else {
			responseObserver.onNext(encodeModelElement((MDObject) element));
			responseObserver.onCompleted();
		}
	}

	@Override
	public void ping(Empty request, StreamObserver<Empty> responseObserver) {
		responseObserver.onNext(Empty.newBuilder().build());
		responseObserver.onCompleted();
	}

	@Override
	public void createInstance(CreateInstanceRequest request, StreamObserver<ModelElement> responseObserver) {
		Project project = Application.getInstance().getProject();
		if (project == null) {
			replyProjectNotOpen(responseObserver);
			return;
		}

		final SessionManager sessionManager = SessionManager.getInstance();
		boolean inSession = sessionManager.isSessionCreated(project);
		if (!inSession) {
			replyNotInSession(responseObserver);
			return;
		}

		final String typeName = request.getTypeName();
		Collection<EClassifier> options = findEClassifier(typeName);
		checkForAmbiguousType(typeName, responseObserver, options);
		if (options.isEmpty()) return;

		EClassifier eClassifier = options.iterator().next();
		if (!(eClassifier instanceof EClass)) {
			replyTypeNotInstantiable(responseObserver, String.format("%s is not an EClassifier", getFullyQualifiedName(eClassifier)));
			return;
		}

		EClass eClass = (EClass) eClassifier;
		if (eClass.isAbstract()) {
			replyTypeNotInstantiable(responseObserver, String.format("%s is abstract", getFullyQualifiedName(eClass)));
		} else {
			final String methodName = "create" + eClass.getName() + "Instance";
			ElementsFactory factory = project.getElementsFactory();
			try {
				Method mCreateInstance = factory.getClass().getMethod(methodName);
				MDObject mdObject = (MDObject) mCreateInstance.invoke(factory);
				if (mdObject instanceof Element) {
					// TODO support a rootElementHyperlink option for indicating where model elements should be created
					ModelElementsManager.getInstance().addElement((Element) mdObject, project.getPrimaryModel());
				}
				responseObserver.onNext(encodeModelElement(mdObject));
				responseObserver.onCompleted();
			} catch (NoSuchMethodException e) {
				LOGGER.error(e.getMessage(), e);
				replyTypeNotInstantiable(responseObserver, String.format("Cannot find method %s in the ElementsFactory", methodName));
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
				replyTypeNotInstantiable(responseObserver, String.format("Invocation of method %s in the ElementsFactory failed", methodName));
			}
		}
	}

	/**
	 * @param responseObserver
	 */
	private <T> void replyNotInSession(StreamObserver<T> responseObserver) {
		responseObserver.onError(Status.fromCode(Code.FAILED_PRECONDITION)
			.withDescription("A session is not open yet")
			.asRuntimeException());
	}

	@Override
	public void openSession(OpenSessionRequest request, StreamObserver<Empty> responseObserver) {
		final Project project = Application.getInstance().getProject();
		if (project == null) {
			replyProjectNotOpen(responseObserver);
			return;
		}

		final SessionManager sessionManager = SessionManager.getInstance();
		boolean inSession = sessionManager.isSessionCreated(project);
		if (inSession) {
			responseObserver.onError(Status.fromCode(Code.FAILED_PRECONDITION)
				.withDescription("A session is already open")
				.asRuntimeException());
		}

		sessionManager.createSession(project, request.getDescription());
		responseObserver.onNext(Empty.newBuilder().build());
		responseObserver.onCompleted();
	}

	@Override
	public void closeSession(Empty request, StreamObserver<Empty> responseObserver) {
		interactWithOpenSession(responseObserver, (project) -> (sm) -> sm.closeSession(project));
	}

	@Override
	public void cancelSession(Empty request, StreamObserver<Empty> responseObserver) {
		interactWithOpenSession(responseObserver, (project) -> (sm) -> sm.cancelSession(project));
	}

	private void interactWithOpenSession(StreamObserver<Empty> responseObserver, Function<Project, Consumer<SessionManager>> call) {
		final Project project = Application.getInstance().getProject();
		if (project == null) {
			replyProjectNotOpen(responseObserver);
			return;
		}

		final SessionManager sessionManager = SessionManager.getInstance();
		boolean inSession = sessionManager.isSessionCreated(project);
		if (!inSession) {
			replyNotInSession(responseObserver);
		}

		call.apply(project).accept(sessionManager);
		responseObserver.onNext(Empty.newBuilder().build());
		responseObserver.onCompleted();
	}
	
	private <T> void replyTypeNotInstantiable(StreamObserver<T> responseObserver, final String description) {
		Metadata metadata = new Metadata();
		Metadata.Key<ErrorInfo> errorKey = ProtoUtils.keyForProto(ErrorInfo.getDefaultInstance());
		metadata.put(errorKey, ErrorInfo.newBuilder()
			.setReason(ModelServiceConstants.REASON_CANNOT_INSTANTIATE_TYPE)
			.setDomain(GRPC_DOMAIN)
			.build());

		responseObserver.onError(Status.fromCode(Code.INVALID_ARGUMENT)
			.withDescription(description).asRuntimeException(metadata));
	}

	private <T> void replyProjectNotOpen(StreamObserver<T> responseObserver) {
		responseObserver.onError(Status.fromCode(Code.FAILED_PRECONDITION)
			.withDescription("Project is not open yet")
			.asRuntimeException());
	}

	private <T> void replyTypeNotFound(StreamObserver<T> responseObserver, final String typeName) {
		Metadata metadata = new Metadata();
		Metadata.Key<ErrorInfo> errorKey = ProtoUtils.keyForProto(ErrorInfo.getDefaultInstance());
		metadata.put(errorKey, ErrorInfo.newBuilder()
			.setReason(ModelServiceConstants.REASON_CANNOT_FIND_TYPE)
			.setDomain(GRPC_DOMAIN)
			.build());

		final StatusRuntimeException error = Status.INVALID_ARGUMENT
			.withDescription(String.format("Cannot find type %s", typeName))
			.asRuntimeException(metadata);

		responseObserver.onError(error);
	}

}