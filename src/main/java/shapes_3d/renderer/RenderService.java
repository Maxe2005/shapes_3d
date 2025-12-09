package shapes_3d.renderer;

import ray_tracer.renderer.DefaultRenderer;
import ray_tracer.renderer.RenderOptions;
import ray_tracer.renderer.RenderTask;
import ray_tracer.renderer.ProgressListener;

import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Service léger pour encapsuler la logique asynchrone de rendu.
 */
public class RenderService {

    private final DefaultRenderer renderer;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final AtomicReference<RenderTask> currentTask = new AtomicReference<>();

    public RenderService() {
        this.renderer = new DefaultRenderer(java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
    }

    public void render(ray_tracer.parsing.Scene scene, ray_tracer.parsing.Camera camera,
                       int width, int height, RenderOptions opts,
                       ProgressListener progressListener,
                       Consumer<BufferedImage> finalImageConsumer) {
        try {
            RenderTask prev = currentTask.get();
            if (prev != null && !prev.isDone()) prev.cancel();
        } catch (Exception ignored) {}

        RenderTask task = renderer.render(scene, camera, width, height, opts);
        if (progressListener != null) task.addProgressListener(progressListener);
        currentTask.set(task);

        exec.execute(() -> {
            try {
                BufferedImage finalImg = task.getFuture().get();
                if (finalImageConsumer != null) finalImageConsumer.accept(finalImg);
            } catch (Exception e) {
                // cancelled or failed
            }
        });
    }

    public void cancel() {
        try {
            RenderTask t = currentTask.get();
            if (t != null && !t.isDone()) t.cancel();
        } catch (Exception ignored) {}
    }

    public void shutdown() {
        try { exec.shutdownNow(); } catch (Exception ignored) {}
    }
}
