package ru.vyarus.dropwizard.guice.module.context.info;

import ru.vyarus.dropwizard.guice.module.context.info.sign.ScanSupport;
import ru.vyarus.dropwizard.guice.module.installer.FeatureInstaller;

import java.util.Set;

/**
 * Extension configuration information.
 *
 * @author Vyacheslav Rusakov
 * @since 09.07.2016
 */
public interface ExtensionItemInfo extends ItemInfo, ScanSupport {

    /**
     * Set could not be empty, because otherwise startup will fail.
     * Most of the time extensions developed for single installer (e.g. resource, heath check etc),
     * but extension may be recognized and installed by multiple installers.
     *
     * @return set of installers installed this extension
     */
    Set<Class<? extends FeatureInstaller>> getInstalledBy();

    /**
     * Lazy beans are not registered in guice by default. Some installers could support this flag in a special way.
     *
     * @return true if extension annotated with
     * {@link ru.vyarus.dropwizard.guice.module.installer.install.binding.LazyBinding}, false otherwise
     */
    boolean isLazy();

    /**
     * Indicates extension management by HK2 instead of guice.
     *
     * @return true if extension annotated with
     * {@link ru.vyarus.dropwizard.guice.module.installer.feature.jersey.HK2Managed}, false otherwise
     */
    boolean isHk2Managed();
}
