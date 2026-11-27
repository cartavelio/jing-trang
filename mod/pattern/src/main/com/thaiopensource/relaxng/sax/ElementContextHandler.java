package com.thaiopensource.relaxng.sax;

import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

/**
 * An optional error handler that observes the element context of validation.
 * A start callback follows validation of buffered preceding text and precedes
 * validation of the element's name, attributes and required content. An end
 * callback follows validation of the element's text and end tag. This allows
 * an error handler to retain structural locations independently of SAX line
 * and column numbers, including for generated documents.
 */
public interface ElementContextHandler extends ErrorHandler {
  /** Clears context before a new validation or when the validator is reset. */
  void reset();

  /** Receives SAX names and attributes before validation of this start tag. */
  void startElement(String namespaceURI, String localName, String qName,
                    Attributes atts) throws SAXException;

  /** Receives SAX names after validation of this end tag. */
  void endElement(String namespaceURI, String localName, String qName)
    throws SAXException;
}
