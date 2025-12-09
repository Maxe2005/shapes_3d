package shapes_3d.gui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import ray_tracer.parsing.Camera;
import ray_tracer.geometry.Point;
import ray_tracer.geometry.Vector;

/**
 * Calcule et applique des déplacements de caméra en réponse aux touches.
 */
public class CameraController {

    /**
     * Gère l'événement clavier et met à jour la caméra associée à la scène si nécessaire.
     * @return true si la caméra a été modifiée
     */
    public boolean handleKeyPressed(KeyEvent ev, ray_tracer.parsing.Scene currentScene) {
        if (currentScene == null) return false;
        Camera cam = currentScene.getCamera();
        if (cam == null) return false;

        Point lookFrom = cam.getLookFrom();
        Point lookAt = cam.getLookAt();
        Vector v = lookFrom.subtraction(lookAt);

        double r = v.norm();
        double theta = Math.acos(v.getY() / r);
        double phi = Math.atan2(v.getZ(), v.getX());

        double angleStep = Math.toRadians(5);
        double zoomStep = r * 0.1;

        KeyCode code = ev.getCode();
        boolean changed = false;

        if (code == KeyCode.LEFT) {
            phi += angleStep;
            changed = true;
        } else if (code == KeyCode.RIGHT) {
            phi -= angleStep;
            changed = true;
        } else if (code == KeyCode.UP) {
            theta -= angleStep;
            changed = true;
        } else if (code == KeyCode.DOWN) {
            theta += angleStep;
            changed = true;
        } else if (code == KeyCode.A) {
            r -= zoomStep;
            if (r < 0.1) r = 0.1;
            changed = true;
        } else if (code == KeyCode.R) {
            r += zoomStep;
            changed = true;
        }

        if (changed) {
            double epsilon = 0.01;
            if (theta < epsilon) theta = epsilon;
            if (theta > Math.PI - epsilon) theta = Math.PI - epsilon;

            double x = r * Math.sin(theta) * Math.cos(phi);
            double y = r * Math.cos(theta);
            double z = r * Math.sin(theta) * Math.sin(phi);

            Vector newOffset = new Vector(x, y, z);
            Point newLookFrom = (Point) lookAt.addition(newOffset);

            Vector forward = lookAt.subtraction(newLookFrom).normalize();
            Vector worldUp = new Vector(0, 1, 0);
            Vector right = forward.vectorialProduct(worldUp).normalize();
            Vector newUp = right.vectorialProduct(forward).normalize();

            Camera newCam = new Camera(
                    newLookFrom.getX(), newLookFrom.getY(), newLookFrom.getZ(),
                    lookAt.getX(), lookAt.getY(), lookAt.getZ(),
                    newUp.getX(), newUp.getY(), newUp.getZ(),
                    cam.getFov()
            );

            currentScene.setCamera(newCam);
            return true;
        }
        return false;
    }
}
