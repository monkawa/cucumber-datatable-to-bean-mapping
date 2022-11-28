package com.deblock.cucumber.datatable.backend;

import com.deblock.cucumber.datatable.annotations.DataTableWithHeader;
import com.deblock.cucumber.datatable.mapper.BeanMapper;
import com.deblock.cucumber.datatable.mapper.typemetadata.CompositeTypeMetadataFactory;
import com.deblock.cucumber.datatable.mapper.typemetadata.collections.CollectionTypeMetadataFactory;
import com.deblock.cucumber.datatable.mapper.typemetadata.custom.CustomTypeMetadataFactory;
import com.deblock.cucumber.datatable.mapper.typemetadata.date.StaticGetTimeService;
import com.deblock.cucumber.datatable.mapper.typemetadata.date.TemporalTypeMetadataFactory;
import com.deblock.cucumber.datatable.mapper.typemetadata.primitive.PrimitiveTypeMetadataFactoryImpl;
import com.deblock.cucumber.datatable.validator.DataTableValidator;
import io.cucumber.core.backend.Backend;
import io.cucumber.core.backend.Glue;
import io.cucumber.core.backend.Snippet;
import io.cucumber.core.resource.ClasspathScanner;
import io.cucumber.core.resource.ClasspathSupport;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static io.cucumber.core.resource.ClasspathSupport.CLASSPATH_SCHEME;

public class DatatableToBeanMappingBackend implements Backend {
    private final ClasspathScanner classFinder;

    public DatatableToBeanMappingBackend(Supplier<ClassLoader> classLoaderSupplier) {
        this.classFinder = new ClasspathScanner(classLoaderSupplier);
    }

    @Override
    public void loadGlue(Glue glue, List<URI> gluePaths) {
        gluePaths.stream()
                .filter(gluePath -> CLASSPATH_SCHEME.equals(gluePath.getScheme()))
                .map(ClasspathSupport::packageName)
                .map(classFinder::scanForClassesInPackage)
                .flatMap(Collection::stream)
                .distinct()
                .forEach(aGlueClass -> {
                    final var annotation = aGlueClass.getAnnotation(DataTableWithHeader.class);
                    if (annotation != null) {
                        registerDataTableDefinition(glue, aGlueClass);
                    }
                });
    }

    private void registerDataTableDefinition(Glue glue, Class<?> aGlueClass) {
        final var typeMetadataFactory = new CompositeTypeMetadataFactory(
                CustomTypeMetadataFactory.INSTANCE,
                new PrimitiveTypeMetadataFactoryImpl(),
                new TemporalTypeMetadataFactory(new StaticGetTimeService())
        );
        typeMetadataFactory.add(new CollectionTypeMetadataFactory(typeMetadataFactory));


        BeanMapper beanMapper = new BeanMapper(aGlueClass, typeMetadataFactory);
        final var validator = new DataTableValidator(beanMapper.headers());
        glue.addDataTableType(new BeanDatatableTypeDefinition(aGlueClass, validator, beanMapper));
        glue.addDataTableType(new BeanListDatatableTypeDefinition(aGlueClass, validator, beanMapper));
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
