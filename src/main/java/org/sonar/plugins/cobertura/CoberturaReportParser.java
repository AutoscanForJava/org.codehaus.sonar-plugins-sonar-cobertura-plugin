/*
 * SonarQube Cobertura Plugin
 * Copyright (C) 2013 SonarSource
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.measures.CoverageMeasuresBuilder;
import org.sonar.api.measures.Measure;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.utils.StaxParser;
import org.sonar.api.utils.XmlParserException;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

import static java.util.Locale.ENGLISH;
import static org.sonar.api.utils.ParsingUtils.parseNumber;

public class CoberturaReportParser {

  private final Project project;
  private final SensorContext context;

  private CoberturaReportParser(SensorContext context, Project project) {
    this.context = context;
    this.project = project;
  }

  /**
   * Parse a Cobertura xml report and create measures accordingly
   */
  public static void parseReport(File xmlFile, SensorContext context, Project project) {
    new CoberturaReportParser(context, project).parse(xmlFile);
  }

  private void parse(File xmlFile) {
    try {
      StaxParser parser = new StaxParser(new StaxParser.XmlStreamHandler() {

        public void stream(SMHierarchicCursor rootCursor) throws XMLStreamException {
          List<File> sourceDirs = Lists.newArrayList();
          rootCursor.advance();
          SMInputCursor cursor = rootCursor.childCursor();
          while (cursor.getNext() != null) {
            if ("sources".equals(cursor.getLocalName())) {
              collectSourceDirs(cursor.childElementCursor("source"), sourceDirs);
            } else if ("packages".equals(cursor.getLocalName())) {
              collectPackageMeasures(cursor.childElementCursor("package"), sourceDirs);
            }
          }
        }
      });
      parser.parse(xmlFile);
    } catch (XMLStreamException e) {
      throw new XmlParserException(e);
    }
  }

  private void collectSourceDirs(SMInputCursor source, List<File> sourceDirs) throws XMLStreamException {
    while (source.getNext() != null) {
      sourceDirs.add(new File(source.getElemStringValue()));
    }
  }

  private void collectPackageMeasures(SMInputCursor pack, List<File> sourceDirs) throws XMLStreamException {
    while (pack.getNext() != null) {
      Map<String, CoverageMeasuresBuilder> builderByFilename = Maps.newHashMap();
      collectFileMeasures(pack.descendantElementCursor("class"), builderByFilename);
      for (Map.Entry<String, CoverageMeasuresBuilder> entry : builderByFilename.entrySet()) {
        save(sourceDirs, entry.getKey(), entry.getValue());
      }
    }
  }

  private void save(List<File> sourceDirs, String filename, CoverageMeasuresBuilder coverage) {
    for (File sourceDir : sourceDirs) {
      File file = new File(sourceDir, filename);
      if (file.isFile()) {
        Resource resource = org.sonar.api.resources.File.fromIOFile(file, project);
        if (resourceExists(resource)) {
          for (Measure measure : coverage.createMeasures()) {
            context.saveMeasure(resource, measure);
          }
        }
        break;
      }
    }
  }

  private boolean resourceExists(Resource<?> file) {
    return context.getResource(file) != null;
  }

  private void collectFileMeasures(SMInputCursor clazz, Map<String, CoverageMeasuresBuilder> builderByFilename) throws XMLStreamException {
    while (clazz.getNext() != null) {
      String fileName = clazz.getAttrValue("filename");
      CoverageMeasuresBuilder builder = builderByFilename.get(fileName);
      if (builder == null) {
        builder = CoverageMeasuresBuilder.create();
        builderByFilename.put(fileName, builder);
      }
      collectFileData(clazz, builder);
    }
  }

  private void collectFileData(SMInputCursor clazz, CoverageMeasuresBuilder builder) throws XMLStreamException {
    SMInputCursor line = clazz.childElementCursor("lines").advance().childElementCursor("line");
    while (line.getNext() != null) {
      int lineId = Integer.parseInt(line.getAttrValue("number"));
      try {
        builder.setHits(lineId, (int) parseNumber(line.getAttrValue("hits"), ENGLISH));
      } catch (ParseException e) {
        throw new XmlParserException(e);
      }

      String isBranch = line.getAttrValue("branch");
      String text = line.getAttrValue("condition-coverage");
      if (StringUtils.equals(isBranch, "true") && StringUtils.isNotBlank(text)) {
        String[] conditions = StringUtils.split(StringUtils.substringBetween(text, "(", ")"), "/");
        builder.setConditions(lineId, Integer.parseInt(conditions[1]), Integer.parseInt(conditions[0]));
      }
    }
  }

}
