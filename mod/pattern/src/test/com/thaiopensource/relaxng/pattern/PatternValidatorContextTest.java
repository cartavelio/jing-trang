package com.thaiopensource.relaxng.pattern;

import com.thaiopensource.relaxng.sax.ElementContextHandler;
import com.thaiopensource.relaxng.sax.PatternValidator;
import com.thaiopensource.xml.util.Name;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PatternValidatorContextTest extends SchemaPatternBuilder {
  private PatternValidator validator(ErrorHandler handler) {
    Pattern value = makeValue(new TokenDatatype(), new Name("", "token"), "good", "good");
    Pattern root = makeElement(new SimpleNameClass(new Name("", "root")), value, new LocatorImpl());
    PatternValidator validator = new PatternValidator(root, new ValidatorPatternBuilder(this), handler);
    validator.setDocumentLocator(new LocatorImpl());
    return validator;
  }

  private void invalidNestedDocument(PatternValidator validator, boolean prefix) throws SAXException {
    Attributes atts = new AttributesImpl();
    validator.startDocument();
    validator.startElement("", "root", "root", atts);
    char[] text = "bad".toCharArray();
    validator.characters(text, 0, text.length);
    if (prefix)
      validator.startPrefixMapping("p", "urn:child");
    validator.startElement(prefix ? "urn:child" : "", "item", prefix ? "p:item" : "item", atts);
    validator.endElement(prefix ? "urn:child" : "", "item", prefix ? "p:item" : "item");
    validator.endElement("", "root", "root");
    validator.endDocument();
  }

  @Test
  public void bufferedTextIsReportedBeforeTheNewElementContext() throws SAXException {
    ContextHandler handler = new ContextHandler();
    invalidNestedDocument(validator(handler), false);
    Assert.assertTrue(handler.locations.size() >= 2);
    Assert.assertEquals(handler.locations.get(0), "/root");
    Assert.assertEquals(handler.locations.get(1), "/root/item");
    Assert.assertEquals(handler.stack.size(), 0);
  }

  @Test
  public void bufferedTextBeforePrefixMappingKeepsItsParentContext() throws SAXException {
    ContextHandler handler = new ContextHandler();
    invalidNestedDocument(validator(handler), true);
    Assert.assertTrue(handler.locations.size() >= 2);
    Assert.assertEquals(handler.locations.get(0), "/root");
    Assert.assertEquals(handler.locations.get(1), "/root/p:item");
    Assert.assertEquals(handler.stack.size(), 0);
  }

  @Test
  public void ordinaryErrorHandlersKeepTheSameOrderedFindings() throws SAXException {
    ContextHandler contextual = new ContextHandler();
    MessageHandler ordinary = new MessageHandler();
    invalidNestedDocument(validator(contextual), false);
    invalidNestedDocument(validator(ordinary), false);
    // These ordered findings come from the validator before context callbacks.
    Assert.assertEquals(ordinary.messages, Arrays.asList(
      "text not allowed here; expected data",
      "element \"item\" not allowed anywhere; expected data",
      "element \"root\" incomplete; expected data"));
    Assert.assertEquals(contextual.messages, ordinary.messages);
  }

  @Test
  public void bufferedTextAtEndKeepsTheElementContext() throws SAXException {
    ContextHandler handler = new ContextHandler();
    PatternValidator validator = validator(handler);
    validator.startDocument();
    validator.startElement("", "root", "root", new AttributesImpl());
    char[] text = "bad".toCharArray();
    validator.characters(text, 0, text.length);
    validator.endElement("", "root", "root");
    validator.endDocument();
    Assert.assertEquals(handler.locations.size(), 1);
    Assert.assertEquals(handler.locations.get(0), "/root");
    Assert.assertTrue(handler.stack.isEmpty());
  }

  @Test
  public void missingContentAtEndKeepsTheElementContext() throws SAXException {
    ContextHandler handler = new ContextHandler();
    Pattern item = makeElement(new SimpleNameClass(new Name("", "item")), makeEmpty(), new LocatorImpl());
    Pattern root = makeElement(new SimpleNameClass(new Name("", "root")), item, new LocatorImpl());
    PatternValidator validator = new PatternValidator(root, new ValidatorPatternBuilder(this), handler);
    validator.startDocument();
    validator.startElement("", "root", "root", new AttributesImpl());
    validator.endElement("", "root", "root");
    validator.endDocument();
    Assert.assertEquals(handler.locations.size(), 1);
    Assert.assertEquals(handler.locations.get(0), "/root");
    Assert.assertTrue(handler.stack.isEmpty());
  }

  @Test
  public void resetAndAnotherDocumentDoNotRetainContext() throws SAXException {
    ContextHandler handler = new ContextHandler();
    PatternValidator validator = validator(handler);
    invalidNestedDocument(validator, false);
    List<String> first = new ArrayList<String>(handler.locations);
    handler.stack.add("stale");
    validator.reset();
    Assert.assertEquals(handler.stack.size(), 0);
    handler.locations.clear();
    handler.stack.add("stale");
    invalidNestedDocument(validator, false);
    Assert.assertEquals(handler.locations, first);
  }

  private static class MessageHandler implements ErrorHandler {
    final List<String> messages = new ArrayList<String>();
    public void error(SAXParseException exception) {
      messages.add(exception.getMessage());
    }
    public void warning(SAXParseException exception) { }
    public void fatalError(SAXParseException exception) throws SAXException {
      throw exception;
    }
  }

  private static class ContextHandler extends MessageHandler implements ElementContextHandler {
    final List<String> stack = new ArrayList<String>();
    final List<String> locations = new ArrayList<String>();
    public void reset() {
      stack.clear();
    }
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) {
      stack.add(qName);
    }
    public void endElement(String namespaceURI, String localName, String qName) {
      Assert.assertEquals(stack.remove(stack.size() - 1), qName);
    }
    public void error(SAXParseException exception) {
      super.error(exception);
      StringBuilder path = new StringBuilder();
      for (String name : stack)
        path.append('/').append(name);
      locations.add(path.toString());
    }
  }
}
