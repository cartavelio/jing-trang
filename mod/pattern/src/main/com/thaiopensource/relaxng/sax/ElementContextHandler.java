package com.thaiopensource.relaxng.sax;

import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

/**
 * An optional error handler that observes the element context of validation.
 * A start callback follows validation of buffered preceding text, including
 * text flushed by prefix mappings, and precedes validation of the element's
 * name and attributes. An end callback follows validation of the element's
 * text and end tag, so errors from those checks still have its context. This
 * allows an error handler to retain structural locations independently of SAX line
 * and column numbers, including for generated documents. Consumers own any
 * structural location formatting. Ordinary SAX {@link ErrorHandler}
 * implementations continue to receive the same ordered diagnostics.
 */
public interface ElementContextHandler extends ErrorHandler {
  /**
   * Clears context at {@link PatternValidator#startDocument()} and when
   * {@link PatternValidator#reset()} is called for reuse.
   */
  void reset();

  /**
   * Receives SAX names and attributes before validation of this start tag.
   * Copy attributes if they must be retained beyond the callback.
   */
  void startElement(String namespaceURI, String localName, String qName,
                    Attributes atts) throws SAXException;

  /** Receives SAX names after validation of this end tag. */
  void endElement(String namespaceURI, String localName, String qName)
    throws SAXException;
}
