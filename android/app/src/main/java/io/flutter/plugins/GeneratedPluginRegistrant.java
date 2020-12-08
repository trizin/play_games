package io.flutter.plugins;

import io.flutter.plugin.common.PluginRegistry;
import xyz.luan.games.play.playgames.PlayGamesPlugin;

/**
 * Generated file. Do not edit.
 */
public final class GeneratedPluginRegistrant {
  public static void registerWith(PluginRegistry registry) {
    if (alreadyRegisteredWith(registry)) {
      return;
    }
    PlayGamesPlugin.registerWith(registry.registrarFor("xyz.luan.games.play.playgames.PlayGamesPlugin"));
  }

  private static boolean alreadyRegisteredWith(PluginRegistry registry) {
    final String key = GeneratedPluginRegistrant.class.getCanonicalName();
    if (registry.hasPlugin(key)) {
      return true;
    }
    registry.registrarFor(key);
    return false;
  }
}
