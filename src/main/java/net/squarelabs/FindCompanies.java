package net.squarelabs;

import com.thinkaurelius.titan.core.TitanFactory;
import com.thinkaurelius.titan.core.TitanGraph;
import com.tinkerpop.blueprints.Vertex;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;

public class FindCompanies {
  private static TitanGraph graph;

  public static void main(String[] args) throws Exception {
    long sleepTime = Long.parseLong(args[0]);

    Configuration conf = new BaseConfiguration();
    conf.setProperty("storage.backend", "cassandra");
    conf.setProperty("storage.hostname", System.getProperty("storage.hostname"));
    graph = TitanFactory.open(conf);

    Iterable<Vertex> vertices = graph.query().vertices();
    System.out.println("Got vertices!");
    int i = 0;
    for (Vertex vert : vertices) {
      String type = vert.getProperty("type");
      System.out.println("Vertex " + i++ + " type=" + type);
      if (!"member".equals(type))
        continue;
      String prop = vert.getProperty("tagline");
      if (!StringUtils.isEmpty(prop)) {
        System.out.println("Skipping member with tagline: " + prop.toString());
        continue;
      }
      String name = URLEncoder.encode(vert.getProperty("name"));
      System.out.println(name);
      String url = "https://www.google.com/search?safe=off&q=" + name + "+Denver+site:linkedin.com&cad=h";
      Thread.sleep(sleepTime);
      Document doc = Jsoup.connect(url)
          .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
          .header("Upgrade-Insecure-Requests", "1")
          .userAgent("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)").get();
      Elements elements = doc.select("#ires .g .slp");
      if (elements.size() == 0) {
        System.out.println("Elements not found!");
        continue;
      }
      Element el = findPerson(elements);
      if (el == null) {
        System.out.println("Person not found!");
        vert.setProperty("tagline", "Not Found");
        graph.commit();
        continue;
      }
      String tagLine = el.text();
      String href = el.parent().parent().select(".r a").attr("href");
      String title = el.parent().parent().select(".r a").text();
      System.out.println("tagLine=" + tagLine);
      System.out.println("href=" + href);
      System.out.println("title=" + title);
      vert.setProperty("tagline", tagLine);
      vert.setProperty("href", href);
      vert.setProperty("title", title);
      graph.commit();
      System.out.println("Saved!");
    }

  }

  private static Element findPerson(Elements elements) {
    for (Element el : elements) {
      String text = el.text();
      if (!text.contains("Denver")
          && !text.contains("Boulder")
          && !text.contains("Golden")
          && !text.contains("Colorado")
          ) {
        System.out.println("Skipping tagline: " + text);
        continue;
      }
      return el;
    }
    return null;
  }

}
