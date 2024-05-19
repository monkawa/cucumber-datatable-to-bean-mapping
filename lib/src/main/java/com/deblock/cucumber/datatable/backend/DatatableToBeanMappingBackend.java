package com.deblock.cucumber.datatable.backend;

import com.deblock.cucumber.datatable.annotations.DataTableWithHeader;
import com.deblock.cucumber.datatable.annotations.Ignore;
import com.deblock.cucumber.datatable.mapper.DatatableMapper;
import com.deblock.cucumber.datatable.mapper.GenericMapperFactory;
import com.deblock.cucumber.datatable.mapper.MapperFactory;
import com.deblock.cucumber.datatable.mapper.typemetadata.CompositeTypeMetadataFactory;
import com.deblock.cucumber.datatable.mapper.typemetadata.collections.CollectionTypeMetadataFactory;
import com.deblock.cucumber.datatable.mapper.typemetadata.custom.CustomTypeMetadataFactory;
import com.deblock.cucumber.datatable.mapper.typemetadata.date.StaticGetTimeService;
import com.deblock.cucumber.datatable.mapper.typemetadata.date.TemporalTypeMetadataFactory;
import com.deblock.cucumber.datatable.mapper.typemetadata.enumeration.EnumTypeMetadataFactory;
import com.deblock.cucumber.datatable.mapper.typemetadata.map.MapTypeMetadataFactory;
import com.deblock.cucumber.datatable.mapper.typemetadata.primitive.PrimitiveTypeMetadataFactoryImpl;
import com.deblock.cucumber.datatable.validator.DataTableValidator;
import io.cucumber.core.backend.Backend;
import io.cucumber.core.backend.Glue;
import io.cucumber.core.backend.Snippet;
import io.cucumber.core.resource.ClasspathScanner;
import io.cucumber.core.resource.ClasspathSupport;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.cucumber.core.resource.ClasspathSupport.CLASSPATH_SCHEME;

public class DatatableToBeanMappingBackend implements Backend {
    private final ClasspathScanner classFinder;

    public DatatableToBeanMappingBackend(Supplier<ClassLoader> classLoaderSupplier) {
        this.classFinder = new ClasspathScanner(classLoaderSupplier);
    }

    @Override
    public void loadGlue(Glue glue, List<URI> gluePaths) {
        final var customTypeMetadataFactory = new CustomTypeMetadataFactory(this.classFinder, gluePaths);
        var typeMetadataFactory = new CompositeTypeMetadataFactory(
            customTypeMetadataFactory,
            new PrimitiveTypeMetadataFactoryImpl(),
            new TemporalTypeMetadataFactory(new StaticGetTimeService()),
            new EnumTypeMetadataFactory(),
            new MapTypeMetadataFactory()
        );
        typeMetadataFactory.add(new CollectionTypeMetadataFactory(typeMetadataFactory));
        final var mapperFactory = new GenericMapperFactory(typeMetadataFactory);

        gluePaths.stream()
                .filter(gluePath -> CLASSPATH_SCHEME.equals(gluePath.getScheme()))
                .map(ClasspathSupport::packageName)
                .map(classFinder::scanForClassesInPackage)
                .flatMap(Collection::stream)
                .filter(DatatableToBeanMappingBackend::isRegisterable)
                .distinct()
                .forEach(aGlueClass -> {
                        registerDataTableDefinition(glue, aGlueClass, mapperFactory);
                });
    }

    private static void registerDataTableDefinition(Glue glue, Class<?> aGlueClass, MapperFactory mapperFactory) {
        DatatableMapper datatableMapper = mapperFactory.build(aGlueClass);
        final var validator = new DataTableValidator(datatableMapper.headers(), false);
        glue.addDataTableType(new BeanDatatableTypeDefinition(aGlueClass, validator, datatableMapper));
        glue.addDataTableType(new BeanListDatatableTypeDefinition(aGlueClass, validator, datatableMapper));
    }

	private static boolean isRegisterable(Class<?> clazz) {
		if (clazz.isAnnotationPresent(Ignore.class)) {
			return false;
		}
		return clazz.isRecord()
				|| clazz.isAnnotationPresent(DataTableWithHeader.class)
				|| hasPublicSetters(clazz)
				|| hasPublicNonFinalField(clazz);
	}

	private static boolean hasPublicNonFinalField(Class<?> clazz) {
		return Stream
				.of(clazz.getDeclaredFields())
				.filter(f -> !Modifier.isFinal(f.getModifiers()))
				.anyMatch(f -> Modifier.isPublic(f.getModifiers()));
	}

	private static boolean hasPublicSetters(Class<?> clazz) {
		return Stream
				.of(clazz.getMethods())
				.filter(m -> Modifier.isPublic(m.getModifiers()))
				.filter(m -> m.getName().startsWith("set"))
				.filter(m -> m.getParameterCount() == 1)
				.findFirst()
				.isPresent();
	}
    
    @Override
    public void buildWorld() {

    }

    @Override
    public void disposeWorld() {

    }

    @Override
    public Snippet getSnippet() {
        return null;
    }
}
