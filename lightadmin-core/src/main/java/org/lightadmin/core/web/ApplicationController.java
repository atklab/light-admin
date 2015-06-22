/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lightadmin.core.web;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.commons.beanutils.PropertyUtils;
import org.lightadmin.core.config.LightAdminConfiguration;
import org.lightadmin.core.config.domain.DomainTypeAdministrationConfiguration;
import org.lightadmin.core.config.domain.GlobalAdministrationConfiguration;
import org.lightadmin.core.persistence.repository.DynamicJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.multiaction.NoSuchRequestHandlingMethodException;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath;

@Controller
@SuppressWarnings({"unused", "unchecked"})
public class ApplicationController {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationController.class);

    public static final String DOMAIN_TYPE_ADMINISTRATION_CONFIGURATION_KEY = "domainTypeAdministrationConfiguration";
    public static final String BEAN_FACTORY_KEY = "beanFactory";

    @Autowired
    private GlobalAdministrationConfiguration configuration;

    @Autowired
    private ConfigurableApplicationContext appContext;

    @Autowired
    private LightAdminConfiguration lightAdminConfiguration;

    @Autowired
    @Qualifier("defaultConversionService")
    private ConversionService conversionService;

    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception ex) {
        return new ModelAndView("error-page").addObject("exception", ex);
    }

    @ExceptionHandler(NoSuchRequestHandlingMethodException.class)
    @RequestMapping(value = "/page-not-found", method = RequestMethod.GET)
    public String handlePageNotFound() {
        return "page-not-found";
    }

    @ResponseStatus(FORBIDDEN)
    @RequestMapping(value = "/access-denied", method = RequestMethod.GET)
    public String handleAccessDenied() {
        return "access-denied";
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String login() {
        return "login";
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String root() {
        return redirectTo("/dashboard");
    }

    @RequestMapping(value = "/dashboard", method = RequestMethod.GET)
    public String dashboard() {
        return "dashboard-view";
    }

    @RequestMapping(value = "/domain/{domainType}", method = RequestMethod.GET)
    public String list(@PathVariable String domainType, Model model) {
        addDomainTypeConfigurationToModel(domainType, model);

        return "list-view";
    }

    @RequestMapping(value = "/domain/{domainTypeName}/{entityId}", method = RequestMethod.GET)
    public String show(@PathVariable String domainTypeName, @PathVariable String entityId, Model model) {
        addDomainTypeConfigurationToModel(domainTypeName, model);

        final Object entity = findEntityOfDomain(entityId, domainTypeName);
        if (entity == null) {
            return pageNotFound();
        }

        model.addAttribute("entity", entity);
        return "show-view";
    }

    @RequestMapping(value = "/domain/{domainTypeName}/{entityId}/edit", method = RequestMethod.GET)
    public String edit(@PathVariable String domainTypeName, @PathVariable String entityId, Model model) {
        addDomainTypeConfigurationToModel(domainTypeName, model);

        final Object entity = findEntityOfDomain(entityId, domainTypeName);
        if (entity == null) {
            return pageNotFound();
        }

        model.addAttribute("entity", entity);
        return "edit-view";
    }

    @RequestMapping(value = "/domain/{domainTypeName}/{entityId}/clone", method = RequestMethod.GET)
    public String clone(@PathVariable String domainTypeName, @PathVariable String entityId, Model model) {
        addDomainTypeConfigurationToModel(domainTypeName, model);

        final Object id = cloneEntityOfDomain(entityId, domainTypeName);
        if (id == null) {
            return pageNotFound();
        }

        return redirectTo("/domain/" + domainTypeName + "/" + conversionService.convert(id, String.class) + "/edit");
    }

    @RequestMapping(value = "/domain/{domainTypeName}/{entityId}/edit-dialog", method = RequestMethod.GET)
    public String editDialog(@PathVariable String domainTypeName, @PathVariable String entityId, Model model) {
        edit(domainTypeName, entityId, model);

        return "edit-dialog-view";
    }

    @RequestMapping(value = "/domain/{domainTypeName}/create", method = RequestMethod.GET)
    public String create(@PathVariable String domainTypeName, Model model) {
        addDomainTypeConfigurationToModel(domainTypeName, model);

        return "create-view";
    }

    @RequestMapping(value = "/domain/{domainTypeName}/create-dialog", method = RequestMethod.GET)
    public String createDialog(@PathVariable String domainTypeName, Model model) {
        create(domainTypeName, model);

        return "create-dialog-view";
    }

    private String pageNotFound() {
        return redirectTo("/page-not-found");
    }

    private Object findEntityOfDomain(String entityId, String domainTypeName) {
        DomainTypeAdministrationConfiguration domainTypeConfiguration = configuration.forEntityName(domainTypeName);
        DynamicJpaRepository repository = domainTypeConfiguration.getRepository();

        PersistentEntity persistentEntity = domainTypeConfiguration.getPersistentEntity();
        Serializable id = (Serializable) conversionService.convert(entityId, persistentEntity.getIdProperty().getActualType());

        return repository.findOne(id);
    }

    private Object cloneEntityOfDomain(String entityId, String domainTypeName) {
        DomainTypeAdministrationConfiguration domainTypeConfiguration = configuration.forEntityName(domainTypeName);
        DynamicJpaRepository repository = domainTypeConfiguration.getRepository();

        PersistentEntity persistentEntity = domainTypeConfiguration.getPersistentEntity();
        Serializable id = (Serializable) conversionService.convert(entityId, persistentEntity.getIdProperty().getActualType());

        Object found = repository.findOne(id);

        if (found != null) {
            try {
                final Object newInstance = domainTypeConfiguration.getDomainType().newInstance();
                BeanUtils.copyProperties(found, newInstance, persistentEntity.getIdProperty().getName());

                PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(newInstance);

                for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {

                    if (propertyDescriptor.getReadMethod() != null && propertyDescriptor.getWriteMethod() != null) {
                        Object value = propertyDescriptor.getReadMethod().invoke(newInstance);

                        Object newValue = null;
                        try {
                            if (value instanceof SortedSet) {
                                newValue = new TreeSet(SortedSet.class.cast(value));
                            } else if (value instanceof Set) {
                                newValue = new HashSet(Set.class.cast(value));
                            } else if (value instanceof SortedMap) {
                                newValue = new TreeMap(SortedMap.class.cast(value));
                            } else if (value instanceof Collection) {
                                newValue = new ArrayList(Collection.class.cast(value));
                            } else if (value instanceof Map) {
                                newValue = new HashMap(Map.class.cast(value));
                            }
                        } catch (Throwable t) {
                            if (logger.isWarnEnabled()){
                                logger.warn("Can't clone "+propertyDescriptor.getName(), t);
                            }
                        }

                        if (newValue != null) {
                            propertyDescriptor.getWriteMethod().invoke(newInstance, newValue);
                        }
                    }

                }

                Object saved = repository.saveAndFlush(newInstance);

                PersistentProperty idProperty = persistentEntity.getIdProperty();
                Field idField = idProperty.getField();
                idField.setAccessible(true);
                return idProperty.usePropertyAccess() ? idProperty.getGetter().invoke(saved) : idField.get(saved);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        } else {
            return null;
        }
    }

    private void addDomainTypeConfigurationToModel(String domainTypeName, Model model) {
        model.addAttribute(DOMAIN_TYPE_ADMINISTRATION_CONFIGURATION_KEY, configuration.forEntityName(domainTypeName));
        model.addAttribute(BEAN_FACTORY_KEY, appContext.getAutowireCapableBeanFactory());
    }

    private String redirectTo(final String url) {
        return "redirect:" + absoluteUrlOf(applicationUrl(url));
    }

    private String absoluteUrlOf(String url) {
        return fromCurrentContextPath().path(url).build().toUriString();
    }

    private String applicationUrl(String value) {
        return lightAdminConfiguration.getApplicationUrl(value);
    }
}
