package com.integ.integration.maven.plugin.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.integ.integration.services.contracts.common.CalendarDateDeserializer;
import com.integ.integration.services.contracts.common.CalendarDateSerializer;
import com.integ.integration.services.contracts.common.rest.ResponseCode;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.Annotation;
import org.jboss.forge.roaster.model.JavaClass;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GenerateDTOMojoTest {

    @Test
    public void testDTO() throws MojoFailureException, MojoExecutionException, IOException {
        GenerateDTOMojo generateDTOMojo = new GenerateDTOMojo() {
            public void setupPlugin() {}
        };

        generateDTOMojo.setBuildDirectory(new File("target/generated-sources/xjc"));
        generateDTOMojo.setModelPackage("com.integ.test.dto");
        generateDTOMojo.setProject(new MavenProject());

        generateDTOMojo.execute();

        JavaClass<?> vehicleLookupRequest = (JavaClass<?>) Roaster.parse(new File("target/generated-sources/xjc/com/integ/test/dto/VehicleLookupRequest.java").toURI().toURL());
        assertEquals(1, vehicleLookupRequest.getFields().size());
        assertNotNull(vehicleLookupRequest.getField("vrm"));
        assertEquals(17, vehicleLookupRequest.getMethods().size());
        assertNotNull(vehicleLookupRequest.getMethod("getVrm"));
        assertNotNull(vehicleLookupRequest.getMethod("setVrm", String.class));

        JavaClass<?> vehicle = (JavaClass<?>) Roaster.parse(new File("target/generated-sources/xjc/com/integ/test/dto/Vehicle.java").toURI().toURL());
        assertEquals(Calendar.class.getCanonicalName(), vehicle.getField("registrationDate").getType().getQualifiedName());
        assertEquals(CalendarDateSerializer.class, vehicle.getField("registrationDate").getAnnotation(JsonSerialize.class).getClassValue("using"));
        assertEquals(CalendarDateDeserializer.class, vehicle.getField("registrationDate").getAnnotation(JsonDeserialize.class).getClassValue("using"));
        assertEquals(List.class, vehicle.getField("registrationDates").getAnnotation(JsonSerialize.class).getClassValue("as"));
        assertEquals(List.class, vehicle.getField("registrationDates").getAnnotation(JsonDeserialize.class).getClassValue("as"));
        assertEquals(CalendarDateSerializer.class, vehicle.getField("registrationDates").getAnnotation(JsonSerialize.class).getClassValue("contentUsing"));
        assertEquals(CalendarDateDeserializer.class, vehicle.getField("registrationDates").getAnnotation(JsonDeserialize.class).getClassValue("contentUsing"));
        assertEquals(24, vehicle.getFields().size());
        assertEquals(61, vehicle.getMethods().size());

        JavaClass<?> faultDetails = (JavaClass<?>) Roaster.parse(new File("target/generated-sources/xjc/com/integ/test/dto/FaultDetails.java").toURI().toURL());
        assertEquals("java.util.List<String>", faultDetails.getField("hint").getType().getQualifiedNameWithGenerics());
        assertEquals(3, faultDetails.getFields().size());
        assertEquals(20, faultDetails.getMethods().size());

        JavaClass<?> faultResponse = (JavaClass<?>) Roaster.parse(new File("target/generated-sources/xjc/com/integ/test/dto/FaultResponse.java").toURI().toURL());
        assertEquals(JsonTypeInfo.Id.NONE, faultResponse.getAnnotation(JsonTypeInfo.class).getEnumValue(JsonTypeInfo.Id.class, "use"));

        JavaClass<?> notFound = (JavaClass<?>) Roaster.parse(new File("target/generated-sources/xjc/com/integ/test/dto/VehicleNotFoundFailure.java").toURI().toURL());
        assertEquals(Response.Status.NOT_FOUND, notFound.getAnnotation(ResponseCode.class).getEnumValue(Response.Status.class));

        JavaClass<?> positiveResponse = (JavaClass<?>) Roaster.parse(new File("target/generated-sources/xjc/com/integ/test/dto/VehicleLookupResponse.java").toURI().toURL());
        assertEquals(Response.Status.OK, positiveResponse.getAnnotation(ResponseCode.class).getEnumValue(Response.Status.class));

        // Checking whether original type is annotated
        JavaClass<?> abstractResponseOriginal = (JavaClass<?>) Roaster.parse(new File("target/generated-sources/xjc/com/integ/test/dto/AbstractVehicleLookupResponse.java").toURI().toURL());
        assertEquals(0, abstractResponseOriginal.getFields().size());
        assertEquals(13, abstractResponseOriginal.getMethods().size());
        assertNotNull(abstractResponseOriginal.getAnnotation(JsonTypeInfo.class));
        assertEquals(JsonTypeInfo.Id.NONE, abstractResponseOriginal.getAnnotation(JsonTypeInfo.class).getEnumValue(JsonTypeInfo.Id.class, "use"));

        Annotation<?>[] annotationArrayOriginal = abstractResponseOriginal.getAnnotation(JsonSubTypes.class).getAnnotationArrayValue();
        assertEquals("com.integ.test.dto.FaultResponse", annotationArrayOriginal[0].getStringValue("name"));
        assertEquals("com.integ.test.dto.FaultResponse", annotationArrayOriginal[0].getClassValue().getCanonicalName());
        assertEquals("com.integ.test.dto.VehicleLookupResponse", annotationArrayOriginal[1].getStringValue("name"));
        assertEquals("com.integ.test.dto.VehicleLookupResponse", annotationArrayOriginal[1].getClassValue().getCanonicalName());
    }

}
