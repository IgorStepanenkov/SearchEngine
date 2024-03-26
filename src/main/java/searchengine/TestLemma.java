package searchengine;

import searchengine.services.LemmaAnalyzerService;

import java.util.List;
import java.util.Map;

public class TestLemma {
    /**
     * Проверка методов класса {@link LemmaAnalyzerService}
     */
    public static void main(String[] args) {
        LemmaAnalyzerService lemmaAnalyzer = new LemmaAnalyzerService();
        lemmaAnalyzer.testMorphInfo("данных");
        String text = """
                Повторное появление леопарда в Осетии позволяет предположить,
                что леопард постоянно обитает в некоторых районах Северного
                Кавказа.
                """;
        Map<String, Integer> lemmas = lemmaAnalyzer.getLemmas(text, true);
        lemmas.forEach((word, count) -> System.out.println(word + " - " + count));
        System.out.println(lemmaAnalyzer.findFirstLemmas(text, List.of("район"), 500, true));
    }
}
