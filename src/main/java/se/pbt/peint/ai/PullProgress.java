package se.pbt.peint.ai;

/**
 * How far downloading a model has come.
 *
 * @param status what Ollama is doing, e.g. "pulling manifest" or "success"
 * @param completed bytes downloaded so far, over all parts of the model
 * @param total bytes to download, over the parts known so far; 0 before
 *              the first part's size is known
 */
public record PullProgress(String status, long completed, long total) {

    /**
     * @return how much is done, from 0 to 1, or -1 while the size is unknown
     */
    public double fraction() {
        return total > 0 ? Math.min(1.0, (double) completed / total) : -1;
    }
}
