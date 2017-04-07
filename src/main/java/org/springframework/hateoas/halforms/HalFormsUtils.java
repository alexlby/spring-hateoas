/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.hateoas.halforms;

import de.escalon.hypermedia.affordance.ActionDescriptor;
import de.escalon.hypermedia.affordance.ActionInputParameter;
import de.escalon.hypermedia.affordance.ActionInputParameterVisitor;
import de.escalon.hypermedia.affordance.Affordance;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.affordance.Suggest;
import org.springframework.hateoas.affordance.Suggestions;
import org.springframework.hateoas.core.EmbeddedWrapper;
import org.springframework.hateoas.core.EmbeddedWrappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class HalFormsUtils {

	public static Object toHalFormsDocument(final Object object, final ObjectMapper objectMapper) {

		if (object == null) {
			return null;
		}

		if (object instanceof ResourceSupport) {
			ResourceSupport rs = (ResourceSupport) object;
			List<Template> templates = new ArrayList<Template>();
			List<EmbeddedWrapper> embedded = new ArrayList<EmbeddedWrapper>();
			List<Link> links = new ArrayList<Link>();
			process(rs, links, templates, objectMapper);
			return new HalFormsDocument(links, templates, embedded);

		} else { // bean
			return object;
		}
	}

	@SuppressWarnings("unused")
	private static void process(ResourceSupport resource, List<Link> links, List<Template> templates,
			ObjectMapper objectMapper) {

		EmbeddedWrappers wrappers = new EmbeddedWrappers(false);

		for (Link link : resource.getLinks()) {
			if (link instanceof Affordance) {
				Affordance affordance = (Affordance) link;

				// TODO: review! the first ActionDescriptor corresponds to the "self" link therefore does never add a template
				for (int i = 0; i < affordance.getActionDescriptors().size(); i++) {
					ActionDescriptor actionDescriptor = affordance.getActionDescriptors().get(i);
					if (i == 0) {
						links.add(affordance);
					} else {
						String key = actionDescriptor.getSemanticActionType();
						if (true || actionDescriptor.hasRequestBody() || !actionDescriptor.getRequestParamNames().isEmpty()) {
							Template template = templates.isEmpty() ? new Template()
									: new Template(key != null ? key : actionDescriptor.getHttpMethod().toString().toLowerCase());
							template.setContentType(actionDescriptor.getConsumes());

							// there is only one httpmethod??
							template.setMethod(actionDescriptor.getHttpMethod());

							actionDescriptor
									.accept(new TemplateActionInputParameterVisitor(template, actionDescriptor, objectMapper));

							templates.add(template);
						}
					}
				}
			} else {
				links.add(link);
			}
		}
	}

	public static Property getProperty(ActionInputParameter actionInputParameter, ActionDescriptor actionDescriptor,
			Object propertyValue, String name, ObjectMapper objectMapper) {

		Map<String, Object> inputConstraints = actionInputParameter.getInputConstraints();

		// TODO: templated comes from an Input attribute?
		boolean templated = false;

		boolean readOnly = inputConstraints.containsKey(ActionInputParameter.EDITABLE)
				? !((Boolean) inputConstraints.get(ActionInputParameter.EDITABLE)) : true;
		String regex = (String) inputConstraints.get(ActionInputParameter.PATTERN);
		boolean required = inputConstraints.containsKey(ActionInputParameter.REQUIRED)
				? (Boolean) inputConstraints.get(ActionInputParameter.REQUIRED) : false;

		String value = null;

		List<Suggest<Object>> possibleValues = actionInputParameter
				.getPossibleValues(actionDescriptor);

		boolean multi = false;

		if (!possibleValues.isEmpty()) {
			try {
				if (propertyValue != null) {
					if (propertyValue.getClass().isEnum()) {
						value = propertyValue.toString();
					} else {
						value = objectMapper.writeValueAsString(propertyValue);
					}
				}
			} catch (JsonProcessingException e) {}

			if (actionInputParameter.isArrayOrCollection()) {
				multi = true;
			}
			List<Object> values = new ArrayList<Object>();

			for (Suggest<Object> possibleValue : possibleValues) {
				values.add(possibleValue.getValue());
			}

		} else {
			if (propertyValue != null) {
				try {
					if (propertyValue instanceof List || propertyValue.getClass().isArray()) {
						value = objectMapper.writeValueAsString(propertyValue);
					} else {
						value = propertyValue.toString();
					}
				} catch (JsonProcessingException e) {}
			}
		}

		Suggestions suggestions = actionInputParameter.getSuggestions();

		return new Property(name, readOnly, value, null, regex, templated, required, multi,
				suggestions.equals(Suggestions.NONE) ? null : suggestions);
	}

	@RequiredArgsConstructor
	static class TemplateActionInputParameterVisitor implements ActionInputParameterVisitor {

		private final Template template;
		private final ActionDescriptor actionDescriptor;
		private final ObjectMapper objectMapper;

		/*
		 * (non-Javadoc)
		 * @see de.escalon.hypermedia.affordance.ActionInputParameterVisitor#visit(de.escalon.hypermedia.affordance.ActionInputParameter)
		 */
		@Override
		public void visit(ActionInputParameter inputParameter) {

			Property property = getProperty(inputParameter, actionDescriptor, inputParameter.getValue(),
					inputParameter.getName(), objectMapper);

			template.getProperties().add(property);
		}
	}
}