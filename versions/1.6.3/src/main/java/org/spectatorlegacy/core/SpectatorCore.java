package org.spectatorlegacy.core;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.6.3")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"org.spectatorlegacy.core"})
public final class SpectatorCore implements IFMLLoadingPlugin {
  public String[] getASMTransformerClass() {
    return new String[] {"org.spectatorlegacy.core.SpectatorTransformer"};
  }

  public String getModContainerClass() {
    return null;
  }

  public String getSetupClass() {
    return null;
  }

  public void injectData(Map<String, Object> data) {}
}
