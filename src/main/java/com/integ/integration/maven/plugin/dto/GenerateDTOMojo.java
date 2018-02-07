/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.integ.integration.maven.plugin.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.integ.integration.services.contracts.common.*;
import com.integ.integration.services.contracts.common.rest.Request;
import com.integ.integration.services.contracts.common.rest.ResponseCode;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.Field;
import org.jboss.forge.roaster.model.JavaClass;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.impl.AnnotationImpl;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;

/**
 * Run a Karaf instance
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = false)
public class GenerateDTOMojo extends AbstractMojo {

    private final static Logger LOG = LoggerFactory.getLogger(GenerateDTOMojo.class);

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/xjc")
    private File modelSourcesDirectory;

    @Parameter(required = true)
    private String modelPackage;

    @Parameter(defaultValue = "${plugin.version}")
    private String pluginVersion = "unknown";

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    public void execute() throws MojoExecutionException, MojoFailureException {
        setupPlugin();

        File modelSources = new File(modelSourcesDirectory, modelPackage.replace(".", "/"));

        LOG.info("Will generate annotations for POJOs in: " + modelSources);

        try {
            procesModelSourcesDir(modelSources);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    protected void setupPlugin() {
        final PluginDescriptor pluginDescriptor = (PluginDescriptor) getPluginContext().get("pluginDescriptor");
        final ClassRealm classRealm = pluginDescriptor.getClassRealm();
        final File classes = new File(getProject().getBuild().getOutputDirectory());
        try {
            classRealm.addURL(classes.toURI().toURL());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void procesModelSourcesDir(File sources) throws IOException {
        if (sources == null || sources.listFiles() == null) {
            LOG.info("No source files were found in: " + modelSourcesDirectory + "/" + modelPackage.replace(".", "/"));
            return;
        }

        for (File file : sources.listFiles()) {
            if (file.isDirectory()) {
                procesModelSourcesDir(file);
            } else {
                annotateModelClassFile(file);
            }
        }
    }

    private void annotateModelClassFile(File inputFile) throws IOException {
        LOG.info("Processing file: " + inputFile);

        JavaType<?> type = Roaster.parse(inputFile.toURI().toURL());
        if (type.getAnnotation(XmlType.class) == null) {
            LOG.info("Ignoring class:" + type.getQualifiedName() + " as it is not annotated with @XmlType and as such is not a part of a model");
            return;
        }

        if (!(type instanceof JavaClass)) {
            LOG.info("Ignoring class:" + type.getQualifiedName() + " as it is not type class");
            return;
        }

        JavaClass<?> inputSource = (JavaClass<?>) type;

        if (type.getAnnotation(XmlSeeAlso.class) != null) {
            AnnotationImpl annotation = (AnnotationImpl) type.getAnnotation(XmlSeeAlso.class);
            Class<?>[] classes = annotation.getClassArrayValue();
            annotateAbstractTypeWithJackson((JavaClassSource) inputSource, classes);
        }

        if (type.getAnnotation(XmlRootElement.class) != null && type.getAnnotation(Request.class) == null && type.getAnnotation(ResponseCode.class) == null) {
            throw new IllegalArgumentException("Class " + type.getQualifiedName() + " is annotated with XmlRootElement but does not have Request/ResponseCode annotation.");
        }

        for (Field<?> field : inputSource.getFields()) {
            AnnotationImpl annotation = (AnnotationImpl) field.getAnnotation(XmlSchemaType.class);
            if (annotation != null) {
                if (List.class.getCanonicalName().equals(field.getType().getQualifiedName())) {
                    annotateXmlTypeListWithJackson((FieldSource) field, annotation);
                } else {
                    annotateXmlTypeWithJackson((FieldSource) field, annotation);
                }
            }
        }

        FileUtils.write(inputFile, inputSource.toString(), "UTF-8");
        LOG.info("Enriched java class:" + inputFile);
    }

    @SuppressWarnings("unchecked")
    private void annotateXmlTypeWithJackson(FieldSource field, AnnotationImpl annotation) {
        switch (annotation.getStringValue("name")) {
            case "date":
                field.addAnnotation(JsonSerialize.class).setClassValue("using", CalendarDateSerializer.class);
                field.addAnnotation(JsonDeserialize.class).setClassValue("using", CalendarDateDeserializer.class);
                break;
            case "dateTime":
                field.addAnnotation(JsonSerialize.class).setClassValue("using", CalendarDateTimeSerializer.class);
                field.addAnnotation(JsonDeserialize.class).setClassValue("using", CalendarDateTimeDeserializer.class);
                break;
            case "time":
                throw new IllegalArgumentException("Handling time type not implemented");
        }
    }

    @SuppressWarnings("unchecked")
    private void annotateXmlTypeListWithJackson(FieldSource field, AnnotationImpl annotation) {
        switch (annotation.getStringValue("name")) {
            case "date":
                field.addAnnotation(JsonSerialize.class).setClassValue("contentUsing", CalendarDateSerializer.class).setClassValue("as", List.class);
                field.addAnnotation(JsonDeserialize.class).setClassValue("contentUsing", CalendarDateDeserializer.class).setClassValue("as", List.class);
                break;
            case "dateTime":
                field.addAnnotation(JsonSerialize.class).setClassValue("contentUsing", CalendarDateTimeSerializer.class).setClassValue("as", List.class);
                field.addAnnotation(JsonDeserialize.class).setClassValue("contentUsing", CalendarDateTimeDeserializer.class).setClassValue("as", List.class);
                break;
            case "time":
                throw new IllegalArgumentException("Handling time type not implemented");
        }
    }

    private void annotateAbstractTypeWithJackson(JavaClassSource dtoSource, Class<?>[] classes) {
        if (dtoSource.getFields().size() == 0 || containsFailureResponse(classes)) {
            LOG.info("Abstract class: " + dtoSource.getQualifiedName() + " will be annotated with JsonTypeInfo:use=Id.NONE as it has no fields OR any of its super classes are Failure Responses annotated with @ResponseCode");
            dtoSource.addAnnotation(JsonTypeInfo.class)
                    .setEnumArrayValue("use", JsonTypeInfo.Id.NONE);
        } else {
            LOG.info("Abstract class: " + dtoSource.getQualifiedName() + " will be annotated with JsonTypeInfo:use=Id.CLASS,include=As.PROPERTY as it has fields in common OR none of its super classes are Failure Responses annotated with @ResponseCode");
            dtoSource.addAnnotation(JsonTypeInfo.class)
                    .setEnumArrayValue("use", JsonTypeInfo.Id.CLASS)
                    .setEnumArrayValue("include", JsonTypeInfo.As.PROPERTY);
        }

        AnnotationSource<JavaClassSource> annotation = dtoSource.addAnnotation(JsonSubTypes.class);
        for (Class<?> c : classes) {
            annotation.addAnnotationValue(JsonSubTypes.Type.class)
                    .setStringValue("name", c.getCanonicalName())
                    .setClassValue(c);
        }
    }

    private boolean containsFailureResponse(Class<?>[] classes) {
        for (Class<?> c : classes) {
            ResponseCode responseCode = c.getAnnotation(ResponseCode.class);
            if (responseCode != null && responseCode.value() != Response.Status.OK) {
                return true;
            }
        }
        return false;
    }

    public File getBuildDirectory() {
        return modelSourcesDirectory;
    }

    public void setBuildDirectory(File buildDirectory) {
        this.modelSourcesDirectory = buildDirectory;
    }

    public String getModelPackage() {
        return modelPackage;
    }

    public void setModelPackage(String modelPackage) {
        this.modelPackage = modelPackage;
    }

    public MavenProject getProject() {
        return project;
    }

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public File getModelSourcesDirectory() {
        return modelSourcesDirectory;
    }

    public void setModelSourcesDirectory(File modelSourcesDirectory) {
        this.modelSourcesDirectory = modelSourcesDirectory;
    }
}
