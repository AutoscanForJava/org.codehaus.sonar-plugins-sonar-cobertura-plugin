/*
 * Sonar Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.cobertura;

import org.sonar.api.batch.maven.MavenPlugin;
import org.sonar.api.batch.maven.MavenPluginHandler;
import org.sonar.api.batch.maven.MavenSurefireUtils;
import org.sonar.api.resources.Project;
import org.sonar.plugins.cobertura.api.CoberturaUtils;

public class CoberturaMavenPluginHandler implements MavenPluginHandler {

  private final CoberturaSettings settings;

  public CoberturaMavenPluginHandler(CoberturaSettings settings) {
    this.settings = settings;
  }

  public String getGroupId() {
    return CoberturaUtils.COBERTURA_GROUP_ID;
  }

  public String getArtifactId() {
    return CoberturaUtils.COBERTURA_ARTIFACT_ID;
  }

  public String getVersion() {
    return "2.5.1";
  }

  public boolean isFixedVersion() {
    return false;
  }

  public String[] getGoals() {
    return new String[] {"cobertura"};
  }

  public void configure(Project project, MavenPlugin coberturaPlugin) {
    configureCobertura(coberturaPlugin);
    MavenSurefireUtils.configure(project);
  }

  private void configureCobertura(MavenPlugin coberturaPlugin) {
    coberturaPlugin.setParameter("formats/format", "xml");
    coberturaPlugin.setParameter("maxmem", settings.getMaxMemory());
  }
}
