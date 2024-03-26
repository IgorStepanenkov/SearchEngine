package searchengine.services;

import lombok.extern.log4j.Log4j2;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.services.helpers.LemmaSearchResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
@Service
public class LemmaAnalyzerService {
    private final static String SEPARATORS_REGEX = "[\\p{Punct}\\n\\s—©]";
    private final static String SERVICE_WORDS_REGEX =
            "(\\sМЕЖД\\s)|(\\sСОЮЗ\\s)|(\\sПРЕДЛ\\s)|(\\sЧАСТ\\s)|(\\sМС\\s)";
    private final LuceneMorphology luceneMorph;
    private final Pattern patternServiceWords;
    private final Pattern patternSeparators;

    public LemmaAnalyzerService() {
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        patternServiceWords = Pattern.compile(SERVICE_WORDS_REGEX);
        patternSeparators = Pattern.compile(SEPARATORS_REGEX);
    }

    /**
     * Метод разбивает исходный текст на значимые леммы и подсчитывающая количество каждой из них
     *
     * @param text           исходный текст
     * @param logDebugErrors признак отладки (для вывода в лог информации об ошибках морфологического анализа)
     * @return Словарь Лемма - Количество
     */
    public Map<String, Integer> getLemmas(String text, boolean logDebugErrors) {
        HashMap<String, Integer> result = new HashMap<>();
        for (String word : text.toLowerCase().split(SEPARATORS_REGEX)) {
            if (word.isEmpty()) {
                continue;
            }
            try {
                List<String> wordInfos = luceneMorph.getMorphInfo(word);

                if (wordInfos.stream()
                        .anyMatch(str -> {
                            // Так и не понял, почему в REGEX не хочет работать \\b
                            Matcher matcher = patternServiceWords.matcher(str + " ");
                            return matcher.find();
                        })) {
                    continue;
                }
                List<String> wordNormalForms = luceneMorph.getNormalForms(word);
                wordNormalForms.forEach(normalForm -> result.put(normalForm.replace("ё", "е"),
                        result.getOrDefault(normalForm, 0) + 1));
            } catch (WrongCharaterException ex) {
                if (logDebugErrors) {
                    log.debug("Ошибка морфологического анализа: " + word);
                }
            }
        }
        return result;
    }

    /**
     * Метод выводит в лог подробную информацию о морфологическом анализе слов, переданных в исходном тексте
     *
     * @param text исходный текст
     */
    public void testMorphInfo(String text) {
        for (String word : text.toLowerCase().split(SEPARATORS_REGEX)) {
            if (word.isEmpty()) {
                continue;
            }
            try {
                luceneMorph.getMorphInfo(word).forEach(log::debug);
            } catch (WrongCharaterException ex) {
                log.debug("Ошибка морфологического анализа: " + word);
            }
        }
    }

    /**
     * Метод ищет в тексте первое слово (первые слова), соответствующее (ие) одной из лемм из заданного списке
     *
     * @param text           исходный текст
     * @param lemmas         список лемм
     * @param maxDepth       глубина поиска после нахождения первой леммы
     * @param logDebugErrors признак отладки (для вывода в лог информации об ошибках морфологического анализа)
     * @return Список записей {@link LemmaSearchResult}
     */
    public List<LemmaSearchResult> findFirstLemmas(String text, List<String> lemmas,
                                                   int maxDepth, boolean logDebugErrors) {
        List<LemmaSearchResult> result = new ArrayList<>();
        int startIndex = 0;
        String lowerCaseText = text.toLowerCase() + " ";
        int depth = lowerCaseText.length();
        Matcher matcher = patternSeparators.matcher(lowerCaseText);
        while (matcher.find()) {
            if (matcher.start() > depth) {
                return result;
            }
            String word = lowerCaseText.substring(startIndex, matcher.start()).strip();
            if (word.isEmpty()) {
                continue;
            }
            try {
                List<String> wordNormalForms = luceneMorph.getNormalForms(word);
                for (String normalForm : wordNormalForms) {
                    if (lemmas.contains(normalForm.replace("ё", "е"))) {
                        if (result.isEmpty()) {
                            depth = startIndex + maxDepth;
                        }
                        result.add(new LemmaSearchResult(startIndex, matcher.start(), normalForm));
                    }
                }
            } catch (WrongCharaterException ex) {
                if (logDebugErrors) {
                    log.debug("Ошибка морфологического анализа: " + word);
                }
            }
            startIndex = matcher.end();
        }
        return result;
    }
}
