package ru.relex.c14n2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

class DOMCanonicalizerHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(DOMCanonicalizerHandler.class);

	private static final String DEFAULT_NS = "";
	private static final String NS = "xmlns";
	private static final String XML = "xml";
	private static final String XSD = "xsd";

	private static final String CF = "&#x%s;";

	private Parameters parameters;
	private StringBuffer outputBlock;

	private boolean bStart = true;
	private boolean bEnd = false;

	private Map<String, List<NamespaceContextParams>> namespaces;
	private int namespace_counter_value = 0;
	private Map<String, String> sequentialUriMap = new HashMap<String, String>();
	private boolean bSequential = false;

	DOMCanonicalizerHandler(Parameters parameters, StringBuffer outputBlock) {
		this.parameters = parameters;
		this.outputBlock = outputBlock;
		bSequential = parameters.getPrefixRewrite().equals(
				Parameters.SEQUENTIAL);

		namespaces = new HashMap<String, List<NamespaceContextParams>>();

		List<NamespaceContextParams> lst = new ArrayList<NamespaceContextParams>();
		NamespaceContextParams ncp = new NamespaceContextParams();
		if (bSequential) {
			ncp.setNewPrefix(String.format("n%s", namespace_counter_value));
			ncp.setHasOutput(false);
		}
		lst.add(ncp);
		namespaces.put(DEFAULT_NS, lst);

		bStart = true;
		bEnd = false;
	}

	protected void processElement(Node node) {
		LOGGER.debug("processElement: {}", node);

		if (getNodeDepth(node) == 1) {
			bStart = false;
		}

		List<NamespaceContextParams> outNSList = processNamespaces(node);

		StringBuffer output = new StringBuffer();
		String prfx = getNodePrefix(node);
		NamespaceContextParams ncp = getLastElement(prfx);
		String localName = getLocalName(node);
		if (namespaces.containsKey(prfx) && !ncp.getNewPrefix().isEmpty()) {
			output.append(String.format("<%s:%s", ncp.getNewPrefix(), localName));
		} else {
			output.append(String.format("<%s", localName));
		}

		List<Attribute> outAttrsList = processAttributes(node);

		for (int i = outNSList.size() - 1; i > 0; i--) {
			NamespaceContextParams ncp1 = outNSList.get(i);
			for (int j = 0; j < i; j++) {
				NamespaceContextParams ncp2 = outNSList.get(j);
				if (ncp1.getNewPrefix().equals(ncp2.getNewPrefix())
						&& ncp1.getUri().equals(ncp2.getUri())) {
					outNSList.remove(i);
					break;
				}
			}
		}

		for (NamespaceContextParams namespace : outNSList) {
			if ((prfx.equals(namespace.getPrefix()) && !ncp.getNewPrefix()
					.equals(namespace.getNewPrefix()))
					|| outputNSInParent(namespace.getPrefix())) {
				ncp.setHasOutput(false);
				continue;
			}
			ncp.setHasOutput(true);
			String nsName = namespace.getNewPrefix();
			String nsUri = namespace.getUri();
			if (!nsName.equals(DEFAULT_NS)) {
				output.append(String.format(" %s:%s=\"%s\"", NS, nsName, nsUri));
			} else {
				output.append(String.format(" %s=\"%s\"", NS, nsUri));
			}
		}

		for (Attribute attribute : outAttrsList) {
			String attrPrfx = attribute.getPrefix();
			String attrName = attribute.getLocalName();
			String attrValue = attribute.getValue();
			if (!bSequential) {
				if (!attrPrfx.equals(DEFAULT_NS)) {
					output.append(String.format(" %s:%s=\"%s\"", attrPrfx,
							attrName, attrValue));
				} else {
					output.append(String.format(" %s=\"%s\"", attrName,
							attrValue));
				}
			} else {
				if (parameters.getQnameAwareAttributes().size() > 0) {
					if (namespaces.containsKey(attrPrfx)) {
						NamespaceContextParams attrPrfxNcp = getLastElement(attrPrfx);
						for (QNameAwareParameter en : parameters
								.getQnameAwareAttributes()) {
							if (attrName.equals(en.name)
									&& en.ns.equals(attrPrfxNcp.getUri())) {
								int idx = attrValue.indexOf(":");
								if (idx > -1) {
									String attr_value_prfx = attrValue
											.substring(0, idx);
									if (namespaces.containsKey(attr_value_prfx)) {
										attrValue = getLastElement(
												attr_value_prfx).getNewPrefix()
												+ ":"
												+ attrValue.substring(idx + 1);
									}
								}
							}
						}
					}
				}
				String attrNewPrfx = attribute.getNewPrefix();
				if (!attrPrfx.equals("")) {
					output.append(String.format(" %s:%s=\"%s\"", attrNewPrfx,
							attrName, attrValue));
				} else {
					output.append(String.format(" %s=\"%s\"", attrName,
							attrValue));
				}
			}
		}

		output.append(">");

		outputBlock.append(output);
	}

	private boolean outputNSInParent(String prfx) {
		for (Entry<String, List<NamespaceContextParams>> en : namespaces
				.entrySet()) {
			if (!bSequential && !prfx.equals(en.getKey()))
				continue;
			List<NamespaceContextParams> lst = en.getValue();
			if (lst.size() > 1) {
				NamespaceContextParams last = getLastElement(prfx);
				for (int i = 2; i <= lst.size(); i++) {
					NamespaceContextParams prev = getLastElement(en.getKey(),
							-i);
					if (last.getNewPrefix().equals(prev.getNewPrefix())) {
						if (!bSequential
								&& !last.getUri().equals(prev.getUri()))
							return false;
						else if (prev.isHasOutput() == null
								|| prev.isHasOutput())
							return true;
					}
				}
			}
		}
		return false;
	}

	protected void processEndElement(Node node) {
		StringBuffer output = new StringBuffer();
		String prfx = getNodePrefix(node);
		NamespaceContextParams ncp = getLastElement(prfx);
		String localName = getLocalName(node);
		if (namespaces.containsKey(prfx) && !ncp.getNewPrefix().isEmpty()) {
			output.append(String.format("</%s:%s>", ncp.getNewPrefix(),
					localName));
		} else {
			output.append(String.format("</%s>", localName));
		}

		removeNamespaces(node);

		if (getNodeDepth(node) == 1) {
			bEnd = true;
		}

		outputBlock.append(output);
	}

	private void removeNamespaces(Node node) {
		for (String prefix : namespaces.keySet()) {
			List<NamespaceContextParams> nsLevels = namespaces.get(prefix);
			while (!nsLevels.isEmpty()
					&& getLastElement(prefix).getDepth() >= getNodeDepth(node)) {
				nsLevels.remove(nsLevels.size() - 1);
			}
		}

		Iterator<Entry<String, List<NamespaceContextParams>>> it = namespaces
				.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, List<NamespaceContextParams>> en = it.next();
			if (en.getValue().size() == 0)
				it.remove();
		}
	}

	private List<Attribute> processAttributes(final Node node) {
		List<Attribute> outAttrsList = new ArrayList<Attribute>();

		for (int ai = 0; ai < node.getAttributes().getLength(); ai++) {
			Node attr = node.getAttributes().item(ai);

			String prfx = getNodePrefix(attr);
			String localName = getLocalName(attr);
			if (!NS.equals(prfx)
					&& !(DEFAULT_NS.equals(prfx) && NS.equals(attr
							.getNodeName()))) {
				Attribute attribute = new Attribute();
				attribute.setPrefix(prfx);
				attribute.setLocalName(localName);
				attribute.setValue(attr.getNodeValue() != null ? attr
						.getNodeValue() : "");
				if (!attribute.getPrefix().isEmpty()
						&& namespaces.containsKey(attribute.getPrefix())) {
					attribute
							.setNewPrefix(getLastElement(attribute.getPrefix())
									.getNewPrefix());
				} else {
					attribute.setNewPrefix(attribute.getPrefix());
				}

				attribute.setValue(processText(attribute.getValue(), true));
				StringBuffer value = new StringBuffer();
				for (int i = 0; i < attribute.getValue().length(); i++) {
					char codepoint = attribute.getValue().charAt(i);
					if (codepoint == 9 || codepoint == 10 || codepoint == 13) {
						value.append(String.format(CF,
								Integer.toHexString(codepoint).toUpperCase()));
					} else {
						value.append(codepoint);
					}
				}
				attribute.setValue(value.toString());

				outAttrsList.add(attribute);
			}
		}

		if (parameters.isSortAttributes()) {
			Collections.sort(outAttrsList, new Comparator<Attribute>() {
				public int compare(Attribute x, Attribute y) {
					String x_uri, y_uri;
					if (XML.equals(x.getPrefix())) {
						x_uri = node.lookupNamespaceURI(XML);
					} else {
						NamespaceContextParams x_stack = getLastElement(x
								.getPrefix());
						x_uri = x_stack != null ? x_stack.getUri() : "";
					}
					if (XML.equals(y.getPrefix())) {
						y_uri = node.lookupNamespaceURI(XML);
					} else {
						NamespaceContextParams y_stack = getLastElement(y
								.getPrefix());
						y_uri = y_stack != null ? y_stack.getUri() : "";
					}
					return String.format("%s:%s", x_uri, x.getLocalName())
							.compareTo(
									String.format("%s:%s", y_uri,
											y.getLocalName()));
				}
			});
		}

		return outAttrsList;
	}

	private List<NamespaceContextParams> processNamespaces(Node node) {
		addNamespaces(node);

		List<NamespaceContextParams> outNSList = new ArrayList<NamespaceContextParams>();

		int depth = getNodeDepth(node);
		for (String prefix : namespaces.keySet()) {
			NamespaceContextParams ncp = getLastElement(prefix);
			if (ncp.getDepth() != depth) {
				NamespaceContextParams entry = ncp.clone();
				if (entry.isHasOutput() != null && depth > 0)
					entry.setHasOutput(false);
				entry.setDepth(depth);
				namespaces.get(prefix).add(entry);
				ncp = entry;
			}
			if (ncp.isHasOutput() != null && !ncp.isHasOutput()) {
				if (isPrefixVisible(node, prefix)) {
					NamespaceContextParams entry = ncp.clone();
					entry.setPrefix(prefix);
					outNSList.add(entry);
				} else
					continue;
				ncp.setHasOutput(true);
			}
		}

		if (bSequential) {
			Collections.sort(outNSList,
					new Comparator<NamespaceContextParams>() {
						public int compare(NamespaceContextParams x,
								NamespaceContextParams y) {
							return x.getUri().compareTo(y.getUri());
						}
					});
			int counter_value = namespace_counter_value;

			for (NamespaceContextParams entry : outNSList) {
				List<NamespaceContextParams> lst = namespaces.get(entry
						.getPrefix());
				boolean b = false;
				NamespaceContextParams ncp = getLastElement(entry.getPrefix());
				for (int j = lst.size() - 2; j >= 0; j--) {
					NamespaceContextParams prntNcp = lst.get(j);
					if (prntNcp.getUri().equals(entry.getUri())
							&& (prntNcp.isHasOutput() || !prntNcp.getPrefix()
									.equals(ncp.getNewPrefix()))) {
						b = true;
						ncp.setNewPrefix(prntNcp.getNewPrefix());
						break;
					}
				}
				if (!b) {
					if (!sequentialUriMap.containsKey(entry.getUri()))
						sequentialUriMap.put(entry.getUri(),
								String.format("n%s", sequentialUriMap.size()));
					entry.setNewPrefix(sequentialUriMap.get(entry.getUri()));
					ncp.setNewPrefix(entry.getNewPrefix());
					counter_value++;
					for (int j = 2; j <= lst.size(); j++)
						if (getLastElement(entry.getPrefix(), -j).getUri()
								.equals(entry.getUri())) {
							getLastElement(entry.getPrefix(), -j).setNewPrefix(
									entry.getNewPrefix());
							break;
						}
				}
			}
			namespace_counter_value = counter_value;
		} else if (parameters.isSortAttributes()) {
			Collections.sort(outNSList,
					new Comparator<NamespaceContextParams>() {
						public int compare(NamespaceContextParams x,
								NamespaceContextParams y) {
							return x.getPrefix().compareTo(y.getPrefix());
						}
					});
		}
		return outNSList;
	}

	private void addNamespaces(Node node) {
		for (int ni = 0; ni < node.getAttributes().getLength(); ni++) {
			Node attr = node.getAttributes().item(ni);
			String prefix = getLocalName(attr);

			String prfxNs = getNodePrefix(attr);

			if (NS.equals(prfxNs)
					|| (DEFAULT_NS.equals(prfxNs) && NS.equals(prefix))) {
				if (NS.equals(prefix)) {
					prefix = "";
				}

				String uri = attr.getNodeValue();

				List<NamespaceContextParams> stack = namespaces.get(prefix);
				if (stack != null
						&& uri.equals(getLastElement(prefix).getUri()))
					continue;

				if (!namespaces.containsKey((prefix))) {
					namespaces.put(prefix,
							new ArrayList<NamespaceContextParams>());
				}
				NamespaceContextParams nsp = new NamespaceContextParams();
				nsp.set(uri, false, prefix, getNodeDepth(node));
				if (namespaces.get(prefix).size() == 0
						|| getNodeDepth(node) != getLastElement(prefix)
								.getDepth())
					namespaces.get(prefix).add(nsp);
				else
					namespaces.get(prefix).set(
							namespaces.get(prefix).size() - 1, nsp);
			}
		}
	}

	private boolean isPrefixVisible(Node node, String prefix) {
		String nodePrefix = getNodePrefix(node);
		if (nodePrefix.equals(prefix)) {
			return true;
		}

		String nodeLocalName = getLocalName(node);
		if (parameters.getQnameAwareElements().size() > 0) {
			NamespaceContextParams ncp = getLastElement(prefix);
			String prfx = ncp.getPrefix();
			String childText = node.getTextContent();
			if (childText != null && childText.startsWith(prfx + ":")) {
				NamespaceContextParams attrPrfxNcp = getLastElement(nodePrefix);
				for (QNameAwareParameter en : parameters
						.getQnameAwareElements()) {
					if (nodeLocalName.equals(en.name)
							&& en.ns.equals(attrPrfxNcp.getUri())) {
						return true;
					}
				}
			}
		}
		if (parameters.getQnameAwareXPathElements().size() > 0) {
			NamespaceContextParams ncp = getLastElement(nodePrefix);
			String childText = node.getTextContent();
			for (QNameAwareParameter en : parameters
					.getQnameAwareXPathElements()) {
				if (nodeLocalName.equals(en.name) && ncp.getUri().equals(en.ns)) {
					XPathFactory xfactory = XPathFactory.newInstance();
					XPath xpath = xfactory.newXPath();
					final Set<String> xpathNs = new HashSet<String>();
					xpath.setNamespaceContext(new NamespaceContext() {
						public String getNamespaceURI(String prefix) {
							xpathNs.add(prefix);
							return prefix;
						}

						public String getPrefix(String namespaceURI) {
							return null;
						}

						public Iterator<?> getPrefixes(String namespaceURI) {
							return null;
						}
					});
					try {
						xpath.compile(childText);
						if (xpathNs.contains(prefix))
							return true;
					} catch (XPathExpressionException e) {
					}
				}
			}
		}

		NamespaceContextParams ncp = getLastElement(prefix);
		String prfx = ncp.getPrefix();
		for (int ai = 0; ai < node.getAttributes().getLength(); ai++) {
			Node attr = node.getAttributes().item(ai);
			if (getNodePrefix(attr).equals(prefix)) {
				return true;
			}
			if (parameters.getQnameAwareAttributes().size() > 0) {
				String attrValue = attr.getNodeValue();
				if (attrValue.startsWith(prfx + ":")) {
					String attrLocalName = getLocalName(attr);
					String attrPrefix = getNodePrefix(attr);
					NamespaceContextParams attrPrfxNcp = getLastElement(attrPrefix);
					for (QNameAwareParameter en : parameters
							.getQnameAwareAttributes()) {
						if (attrLocalName.equals(en.name)
								&& en.ns.equals(attrPrfxNcp.getUri())) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	protected void processText(Node node) {
		LOGGER.debug("processText: {}" , node);
		if (getNodeDepth(node) < 2) {
			return;
		}

		String text = node.getNodeValue() != null ? node.getNodeValue() : "";
		// TODO UTF-8

		StringBuffer value = new StringBuffer();
		for (int i = 0; i < text.length(); i++) {
			char codepoint = text.charAt(i);
			if (codepoint == 13) {
				value.append(String.format(CF, Integer.toHexString(codepoint)
						.toUpperCase()));
			} else {
				value.append(codepoint);
			}
		}
		text = value.toString();

		processText(text, false);

		if (parameters.isTrimTextNodes()) {
			boolean b = true;
			for (int ai = 0; ai < node.getParentNode().getAttributes()
					.getLength(); ai++) {
				Node attr = node.getParentNode().getAttributes().item(ai);
				if (XML.equals(getNodePrefix(attr))
						&& "preserve".equals(attr.getNodeValue())
						&& getLocalName(attr).equals("space")) {
					b = false;
					break;
				}
			}
			if (b) {
				text = text.trim();
			}
		}

		if (parameters.getQnameAwareElements().size() > 0 && bSequential) {
			if (text.startsWith(XSD + ":")) {
				if (namespaces.containsKey(XSD)) {
					Node prntNode = node.getParentNode();
					String nodeName = getLocalName(prntNode);
					String nodePrefix = getNodePrefix(prntNode);
					NamespaceContextParams ncp = getLastElement(XSD);
					NamespaceContextParams attrPrfxNcp = getLastElement(nodePrefix);
					for (QNameAwareParameter en : parameters
							.getQnameAwareElements()) {
						if (nodeName.equals(en.name)
								&& en.ns.equals(attrPrfxNcp.getUri())) {
							text = ncp.getNewPrefix()
									+ text.substring(XSD.length());
						}
					}
				}
			}
		}
		if (parameters.getQnameAwareXPathElements().size() > 0 && bSequential) {
			Node prntNode = node.getParentNode();
			String nodeName = getLocalName(prntNode);
			String nodePrefix = getNodePrefix(prntNode);
			NamespaceContextParams ncp = getLastElement(nodePrefix);
			for (QNameAwareParameter en : parameters
					.getQnameAwareXPathElements()) {
				if (nodeName.equals(en.name) && ncp.getUri().equals(en.ns)) {
					String nodeText = node.getTextContent();

					XPathFactory xfactory = XPathFactory.newInstance();
					XPath xpath = xfactory.newXPath();
					final Set<String> xpathNs = new HashSet<String>();
					xpath.setNamespaceContext(new NamespaceContext() {
						public String getNamespaceURI(String prefix) {
							xpathNs.add(prefix);
							return prefix;
						}

						public String getPrefix(String namespaceURI) {
							return null;
						}

						public Iterator<?> getPrefixes(String namespaceURI) {
							return null;
						}
					});
					try {
						xpath.compile(nodeText);
						if (xpathNs.size() > 0) {
							for (String prfx : xpathNs) {
								text = text.replace(prfx + ":",
										getLastElement(prfx).getNewPrefix()
												+ ":"); // TODO
							}
						}
					} catch (XPathExpressionException e) {
					}
				}
			}
		}

		outputBlock.append(text);
	}

	private String processText(String text, boolean bAttr) {
		text = text.replace("&", "&amp;");
		text = text.replace("<", "&lt;");
		if (!bAttr)
			text = text.replace(">", "&gt;");
		else
			text = text.replace("\"", "&quot;");
		text = text.replace("#xD", "&#xD;"); // x9 xA
		return text;
	}

	protected void processPI(Node node) {
		LOGGER.debug("processPI: {}" ,node);
		String nodeName = node.getNodeName();
		String nodeValue = node.getNodeValue() != null ? node.getNodeValue()
				: "";

		StringBuffer output = new StringBuffer();
		if (bEnd && getNodeDepth(node) == 1) {
			output.append("\n");
		}
		output.append(String.format("<?%s%s?>", nodeName,
				!nodeValue.isEmpty() ? (" " + nodeValue) : ""));
		if (bStart && getNodeDepth(node) == 1) {
			output.append("\n");
		}
		outputBlock.append(output);
	}

	protected void processComment(Node node) {
		LOGGER.debug("processComment: {}",node);
		if (parameters.isIgnoreComments())
			return;

		StringBuffer output = new StringBuffer();
		if (bEnd && getNodeDepth(node) == 1) {
			output.append("\n");
		}
		output.append(String.format("<!--%s-->", node.getNodeValue()));
		if (bStart && getNodeDepth(node) == 1) {
			output.append("\n");
		}
		outputBlock.append(output);
	}

	protected void processCData(Node node) {
		LOGGER.debug("processCData:" + node);
		outputBlock.append(processText(node.getNodeValue(), false));
	}

	protected StringBuffer getOutputBlock() {
		return outputBlock;
	}

	private String getLocalName(Node node) {
		if (node.getLocalName() != null)
			return node.getLocalName();
		String name = node.getNodeName();
		int idx = name.indexOf(":");
		if (idx > -1)
			return name.substring(idx + 1);
		return name;
	}

	private int getNodeDepth(Node node) {
		int i = -1;
		Node prnt = node;
		do {
			i++;
			prnt = prnt.getParentNode();
		} while (prnt != null);
		return i;
	}

	private NamespaceContextParams getLastElement(String key) {
		return getLastElement(key, -1);
	}

	private NamespaceContextParams getLastElement(String key, int shift) {
		List<NamespaceContextParams> lst = namespaces.get(key);
		return lst.size() + shift > -1 ? lst.get(lst.size() + shift) : null;
	}

	private String getNodePrefix(Node node) {
		String prfx = node.getPrefix();
		if (prfx == null || prfx.isEmpty()) {
			prfx = "";
			String name = node.getNodeName();
			int idx = name.indexOf(":");
			if (idx > -1)
				return name.substring(0, idx);
		}
		return prfx;
	}
}