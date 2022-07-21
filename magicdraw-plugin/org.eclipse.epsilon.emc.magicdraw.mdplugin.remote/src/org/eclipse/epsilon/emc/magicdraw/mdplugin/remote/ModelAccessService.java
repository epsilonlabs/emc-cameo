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

import static org.eclipse.epsilon.emc.magicdraw.mdplugin.remote.emf.ModelUtils.findEClassifier;
import static org.eclipse.epsilon.emc.magicdraw.mdplugin.remote.emf.ModelUtils.getFullyQualifiedName;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.epsilon.emc.magicdraw.mdplugin.remote.emf.ObjectEncoder;
import org.eclipse.epsilon.emc.magicdraw.modelapi.AllOfRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.CreateInstanceRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Empty;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetElementByIDRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetEnumerationValueRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetEnumerationValueResponse;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetFeatureValueRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetTypeRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementType;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementTypeReference;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceConstants;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceGrpc;
import org.eclipse.epsilon.emc.magicdraw.modelapi.OpenSessionRequest;
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

/**
 * Exposes the model opened in a running MagicDraw instance over gRPC.
 */
public class ModelAccessService extends ModelServiceGrpc.ModelServiceImplBase {
	private static final Logger LOGGER = LoggerFactory.getLogger(ModelAccessService.class);
	private static final String GRPC_DOMAIN = ModelAccessService.class.getPackage().getName();

	private final ObjectEncoder encoder = new ObjectEncoder();

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

		final ModelElementCollection allOfResponse = encoder.encodeAllOf(eClassifier, root, request.getOnlyExactType());
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
		} else {
			final Object rawValue = mdObject.eGet(eFeature);
			if (rawValue != null) {
				encoder.encode(eFeature, vBuilder, rawValue);
			}
		}

		responseObserver.onNext(vBuilder.build());
		responseObserver.onCompleted();
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
			responseObserver.onNext(encoder.encode((MDObject) element));
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
				responseObserver.onNext(encoder.encode(mdObject));
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
	
	/**
	 * @param responseObserver
	 */
	private <T> void replyNotInSession(StreamObserver<T> responseObserver) {
		responseObserver.onError(Status.fromCode(Code.FAILED_PRECONDITION)
			.withDescription("A session is not open yet")
			.asRuntimeException());
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

}