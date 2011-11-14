package de.haw.hamburg.inf.pro.ws11.petersenwienrich;


/**
 * The Class FrequencyCounter.
 * <p/>
 * A Helper to determine the Frequency of a continuously repeated event. When
 * the event occurs, the triggerEvent method is to be called.
 * <p/>
 * The frequency can than be obtained by calling the getFrequency method.
 */
public class FrequencyCounter {

    /**
     * The current in frame time.
     */
    private long currentInFrame;

    /**
     * The first in frame time.
     */
    private long firstInFrame;

    /**
     * The in frames.
     */
    private int inFrames;

    /**
     * The frequency.
     */
    private int frequency;

    /**
     * Gets the frequency.
     *
     * @return the frequency
     */
    public synchronized final int getFrequency() {
        return frequency;
    }

    /**
     * Instantiates a new frequency counter.
     */
    public FrequencyCounter() {
        this.currentInFrame = System.currentTimeMillis();
        this.firstInFrame = 0;
        this.inFrames = 0;
        this.frequency = 0;
    }

    /**
     * Trigger count.
     */
    public void triggerCount() {
        this.inFrames++;
        this.currentInFrame = System.currentTimeMillis();
        if (this.currentInFrame > this.firstInFrame + 1000) {
            this.firstInFrame = this.currentInFrame;
            this.frequency = this.inFrames;
            this.inFrames = 0;
        }
    }
}
