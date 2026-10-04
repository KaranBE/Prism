package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.model.ModelTier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spec requires the "auto" classifier to beat a length-only baseline on prompts
 * specifically designed to fool a length-only heuristic: short-but-hard, and long-but-trivial.
 */
class DifficultyClassifierServiceTest {

    private final DifficultyClassifierService classifier = new DifficultyClassifierService();

    @Test
    void shortButHardPromptRoutesToSmart() {
        String prompt = "Prove that sqrt(2) is irrational.";
        // A length-only baseline (short == easy) would send this to "fast" - the classifier must not.
        assertThat(classifier.classify(prompt)).isEqualTo(ModelTier.SMART);
    }

    @Test
    void longButTrivialPromptRoutesToFast() {
        String prompt = "Please continue this story about a dog who likes to play fetch in the park "
                + "every single sunny afternoon with his best friend, a golden retriever named Max, "
                + "who also enjoys long walks and chasing squirrels around the old oak tree near the pond "
                + "where the ducks like to swim in circles all day long while children watch nearby.";
        // A length-only baseline (long == hard) would send this to "smart" - the classifier must not.
        assertThat(classifier.classify(prompt)).isEqualTo(ModelTier.FAST);
    }

    @Test
    void trivialGreetingRoutesToFast() {
        assertThat(classifier.classify("hi, how are you?")).isEqualTo(ModelTier.FAST);
    }

    @Test
    void codeDebuggingRequestRoutesToSmart() {
        String prompt = "Debug this: for(i=0;i<n;i++) { arr[i] = arr[i+1]; } it throws an IndexOutOfBounds.";
        assertThat(classifier.classify(prompt)).isEqualTo(ModelTier.SMART);
    }
}
