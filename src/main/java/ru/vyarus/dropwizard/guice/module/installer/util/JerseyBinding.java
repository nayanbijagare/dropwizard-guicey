package ru.vyarus.dropwizard.guice.module.installer.util;

import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.binder.ScopedBindingBuilder;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.InjectionResolver;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.hk2.utilities.reflection.ParameterizedTypeImpl;
import ru.vyarus.dropwizard.guice.module.installer.feature.jersey.GuiceManaged;
import ru.vyarus.dropwizard.guice.module.installer.feature.jersey.HK2Managed;
import ru.vyarus.dropwizard.guice.module.jersey.support.GuiceComponentFactory;
import ru.vyarus.dropwizard.guice.module.jersey.support.JerseyComponentProvider;
import ru.vyarus.dropwizard.guice.module.jersey.support.LazyGuiceFactory;
import ru.vyarus.java.generics.resolver.GenericsResolver;
import ru.vyarus.java.generics.resolver.context.GenericsContext;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.reflect.Type;
import java.util.List;

/**
 * HK binding utilities. Supplement {@link ru.vyarus.dropwizard.guice.module.installer.install.JerseyInstaller}.
 *
 * @author Vyacheslav Rusakov
 * @since 21.11.2014
 */
public final class JerseyBinding {

    private JerseyBinding() {
    }

    /**
     * @return true if hk bridge is available in classpath, false otherwise
     */
    public static boolean isBridgeAvailable() {
        try {
            Class.forName("org.jvnet.hk2.guice.bridge.api.GuiceBridge");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    /**
     * When guice-first mode used (default) all jersey extensions are instantiated by guice and only if
     * {@linkplain HK2Managed} annotation set on bean - it will be instantiated by hk.
     * <p>
     * When hk-first mode used
     * ({@linkplain ru.vyarus.dropwizard.guice.module.installer.InstallersOptions#HkExtensionsManagedByGuice})
     * all jersey extensions are instantiated by hk and only if {@linkplain GuiceManaged} annotation set on bean - it
     * will be instantiated by guice.
     *
     * @param type           type to check
     * @param guiceFirstMode true when guice used by default for jersey extensions management, false when
     *                       hk used by default
     * @return true if type should be managed by hkm false when type should managed by guice.
     * @see ru.vyarus.dropwizard.guice.module.installer.InstallersOptions#HkExtensionsManagedByGuice
     * @see HK2Managed
     * @see GuiceManaged
     */
    public static boolean isHK2Managed(final Class<?> type, final boolean guiceFirstMode) {
        return guiceFirstMode
                ? type.isAnnotationPresent(HK2Managed.class)
                : !type.isAnnotationPresent(GuiceManaged.class);
    }

    /**
     * Binds component into HK context. If component is annotated with {@link HK2Managed}, then registers type,
     * otherwise register guice "bridge" factory around component.
     *
     * @param binder    hk binder
     * @param injector  guice injector
     * @param type      component type
     * @param hkManaged true if bean must be managed by hk, false to bind guice managed instance
     * @see ru.vyarus.dropwizard.guice.module.jersey.support.GuiceComponentFactory
     */
    public static void bindComponent(final AbstractBinder binder, final Injector injector, final Class<?> type,
                                     final boolean hkManaged) {
        if (hkManaged) {
            binder.bindAsContract(type).in(Singleton.class);
        } else {
            // default case: simple service registered directly (including resource)
            binder.bindFactory(new GuiceComponentFactory<>(injector, type)).to(type);
        }
    }

    /**
     * Binds hk {@link Factory}. If bean is {@link HK2Managed} then registered directly as
     * factory. Otherwise register factory through special "lazy bridge" to delay guice factory bean instantiation.
     * Also registers factory directly (through wrapper to be able to inject factory by its type).
     *
     * @param binder    hk binder
     * @param injector  guice injector
     * @param type      factory to bind
     * @param hkManaged true if bean must be managed by hk, false to bind guice managed instance
     * @param <T>       actual type (used to workaround type checks)
     * @see ru.vyarus.dropwizard.guice.module.jersey.support.LazyGuiceFactory
     * @see ru.vyarus.dropwizard.guice.module.jersey.support.GuiceComponentFactory
     */
    @SuppressWarnings("unchecked")
    public static <T> void bindFactory(final AbstractBinder binder, final Injector injector, final Class<?> type,
                                       final boolean hkManaged) {
        // resolve Factory<T> actual type to bind properly
        final Class<T> res = (Class<T>) GenericsResolver.resolve(type).type(Factory.class).generic(0);
        if (hkManaged) {
            binder.bindFactory((Class<Factory<T>>) type)
                    .to(res)
                    .in(Singleton.class);
        } else {
            binder.bindFactory(new LazyGuiceFactory(injector, type))
                    .to(res);
            // binding factory type to be able to autowire factory by name
            binder.bindFactory(new GuiceComponentFactory<>(injector, type))
                    .to(type);
        }
    }

    /**
     * Binds jersey specific component (component implements jersey interface or extends class).
     * Specific binding is required for types directly supported by jersey (e.g. ExceptionMapper).
     * Such types must be bound to target interface directly, otherwise jersey would not be able to resolve them.
     * <p> If type is {@link HK2Managed}, binds directly.
     * Otherwise, use guice "bridge" factory to lazily bind type.</p>
     *
     * @param binder       hk binder
     * @param injector     guice injector
     * @param type         type which implements specific jersey interface or extends class
     * @param specificType specific jersey type (interface or abstract class)
     * @param hkManaged    true if bean must be managed by hk, false to bind guice managed instance
     */
    public static void bindSpecificComponent(final AbstractBinder binder,
                                             final Injector injector,
                                             final Class<?> type,
                                             final Class<?> specificType,
                                             final boolean hkManaged) {
        // resolve generics of specific type
        final GenericsContext context = GenericsResolver.resolve(type).type(specificType);
        final List<Type> genericTypes = context.genericTypes();
        final Type[] generics = genericTypes.toArray(new Type[0]);
        final Type binding = generics.length > 0 ? new ParameterizedTypeImpl(specificType, generics)
                : specificType;
        if (hkManaged) {
            binder.bind(type).to(binding).in(Singleton.class);
        } else {
            // hk cant find different things in different situations, so uniform registration is impossible
            if (InjectionResolver.class.equals(specificType)) {
                binder.bindFactory(new GuiceComponentFactory<>(injector, type))
                        .to(type).in(Singleton.class);
                binder.bind(type).to(binding).in(Singleton.class);
            } else {
                binder.bindFactory(new GuiceComponentFactory<>(injector, type))
                        .to(type).to(binding).in(Singleton.class);
            }
        }
    }

    /**
     * Used to bind jersey beans in guice context (lazily). Guice context is started first, so there is
     * no way to bind instances. Instead "lazy bridge" installed, which will resolve target type on first call.
     * Guice is not completely started and direct injector lookup is impossible here, so lazy injector provider used.
     *
     * @param binder   guice binder
     * @param provider provider for guice injector
     * @param type     jersey type to register
     * @param <T>      type
     * @return scoped binder object to optionally define binding scope.
     * @see ru.vyarus.dropwizard.guice.injector.lookup.InjectorProvider
     */
    public static <T> ScopedBindingBuilder bindJerseyComponent(final Binder binder, final Provider<Injector> provider,
                                                               final Class<T> type) {
        return binder.bind(type).toProvider(new JerseyComponentProvider<>(provider, type));
    }

}
