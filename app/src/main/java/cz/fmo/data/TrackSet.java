package cz.fmo.data;

import android.util.SparseArray;

import java.util.ArrayList;

import cz.fmo.Lib;
import cz.fmo.graphics.FontRenderer;
import cz.fmo.graphics.GL;
import cz.fmo.graphics.TriangleStripRenderer;
import cz.fmo.util.Color;

/**
 * Latest detected tracks that are meant to be kept on screen to allow inspection by the user.
 */
public class TrackSet {
    private static final int NUM_TRACKS = 8;
    private final Object mLock = new Object();
    private final ArrayList<Track> mTracks = new ArrayList<>();
    private SparseArray<Track> mCurrentTrackMap = new SparseArray<>();
    private SparseArray<Track> mPreviousTrackMap = new SparseArray<>();
    private int mWidth = 1;  // width of the source image (not necessarily the screen width)
    private int mHeight = 1; // height of the source image (not necessarily the screen height)
    private int mTrackCounter = 0;
    private static final float[] HUES = {0.f, 45.f, 90.f, 135.f, 180.f, 225.f, 270.f, 315.f};

    private TrackSet() {
    }

    public static TrackSet getInstance() {
        return SingletonHolder.instance;
    }

    /**
     * Adds detections to the correct tracks. If there is no predecessor for a given detection, a
     * new track is created.
     *
     * @param width width of the source image (not the screen)
     * @param height height of the source image (not the screen)
     */
    public void addDetections(Lib.Detection[] detections, int width, int height) {
        synchronized (mLock) {
            mWidth = width;
            mHeight = height;

            // swap the maps
            {
                SparseArray<Track> temp = mCurrentTrackMap;
                mCurrentTrackMap = mPreviousTrackMap;
                mPreviousTrackMap = temp;
            }

            mCurrentTrackMap.clear();
            for (Lib.Detection detection : detections) {
                if (detection.id < 0) {
                    throw new RuntimeException("ID of a detection not specified");
                }

                // get the track of the predecessor
                Track track = mPreviousTrackMap.get(detection.predecessorId);
                if (track == null) {
                    // no predecessor/track not found: make a new track
                    mTrackCounter++;
                    float hue = HUES[mTrackCounter % 8] + 24.56f;
                    track = new Track(hue);
                    // shift to erase the oldest track
                    if (mTracks.size() == NUM_TRACKS) {
                        mTracks.remove(0);
                    }
                    // add the track to the list
                    mTracks.add(track);
                }

                detection.predecessor = track.getLatest();
                track.setLatest(detection);
                mCurrentTrackMap.put(detection.id, track);
            }
        }
    }

    public void generateCurves(TriangleStripRenderer.Buffers b) {
        synchronized (mLock) {
            GL.setIdentity(b.posMat);
            b.posMat[0x0] = 2.f / mWidth;
            b.posMat[0x5] = -2.f / mHeight;
            b.posMat[0xC] = -1.f;
            b.posMat[0xD] = 1.f;
            b.pos.clear();
            b.color.clear();
            b.numVertices = 0;

            for (Track track : mTracks) {
                track.generateCurve(b);
            }

            b.pos.limit(b.numVertices * 2);
            b.color.limit(b.numVertices * 4);
        }
    }

    public void generateLabels(FontRenderer fontRender, int height) {
        synchronized (mLock) {
            Color.RGBA color = new Color.RGBA();
            float hs = ((float) height) / 20.f;
            float ws = hs * FontRenderer.CHAR_STEP_X;
            float mid = height / 2.f;
            int items = mTracks.size();
            float top = mid - 0.5f * (items + 1) * hs;

            color.rgba[0] = 0.0f;
            color.rgba[1] = 0.0f;
            color.rgba[2] = 0.0f;
            color.rgba[3] = 0.5f;
            fontRender.addRectangle(0, mid - 0.5f * (items + 1) * hs, 7 * ws, (items + 1) * hs, color);
            color.rgba[0] = 0.5f;
            color.rgba[1] = 0.5f;
            color.rgba[2] = 0.5f;
            color.rgba[3] = 1.0f;
            fontRender.addString("px/fr", ws, top + 0.5f * hs, hs, color);

            for (int i = 0; i < items; i++) {
                mTracks.get(i).generateLabel(fontRender, hs, ws, top, i);
            }
        }
    }

    public void clear() {
        synchronized (mLock) {
            mTracks.clear();
            mPreviousTrackMap.clear();
            mCurrentTrackMap.clear();
        }
    }

    private static class SingletonHolder {
        static final TrackSet instance = new TrackSet();
    }
}
