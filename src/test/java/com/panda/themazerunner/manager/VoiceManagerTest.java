package com.panda.themazerunner.manager;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class VoiceManagerTest {
    @Test
    void facadeLoadsWhenVoicechatApiIsUnavailable() throws Exception {
        String name = "com.panda.themazerunner.manager.VoiceManager";
        ClassLoader loader =
                new ClassLoader(getClass().getClassLoader()) {
                    @Override
                    protected Class<?> loadClass(String requested, boolean resolve)
                            throws ClassNotFoundException {
                        if (requested.startsWith("de.maxhenkel.voicechat"))
                            throw new ClassNotFoundException(requested);
                        if (!requested.equals(name)) return super.loadClass(requested, resolve);
                        Class<?> existing = findLoadedClass(requested);
                        if (existing != null) return existing;
                        try (var stream =
                                getParent()
                                        .getResourceAsStream(
                                                requested.replace('.', '/') + ".class")) {
                            byte[] bytes = stream.readAllBytes();
                            Class<?> result = defineClass(requested, bytes, 0, bytes.length);
                            if (resolve) resolveClass(result);
                            return result;
                        } catch (java.io.IOException e) {
                            throw new ClassNotFoundException(requested, e);
                        }
                    }
                };
        Class<?> facade = Class.forName(name, true, loader);
        assertNotNull(facade.getConstructor(JavaPlugin.class).newInstance(new Object[] {null}));
        assertTrue(facade.getDeclaredMethods().length > 0);
        assertThrows(
                ClassNotFoundException.class,
                () -> loader.loadClass("de.maxhenkel.voicechat.api.VoicechatPlugin"));
    }
}
