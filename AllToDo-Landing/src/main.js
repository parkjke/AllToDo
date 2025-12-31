
const images = [
    './assets/bg_mobile_1.png',
    './assets/bg_mobile_2.png',
    './assets/bg_mobile_3.png',
    './assets/bg_dashboard.png',
    './assets/bg_mobile_4.png',
    './assets/bg_mobile_5.png',
    './assets/bg_mobile_6.png'
];

function initBackground() {
    const container = document.createElement('div');
    container.id = 'bg-container';
    container.style.position = 'fixed';
    container.style.top = '0';
    container.style.left = '0';
    container.style.width = '100vw';
    container.style.height = '100vh';
    container.style.zIndex = '0';
    container.style.overflow = 'hidden';
    container.style.pointerEvents = 'none'; // Click-through

    // Base dark background
    container.style.backgroundColor = '#0a0a0a';
    container.style.backgroundImage = 'radial-gradient(circle at 50% 10%, rgba(0, 230, 118, 0.05) 0%, transparent 50%)';

    document.body.appendChild(container);

    let currentIndex = 0;

    // Function to spawn a single image
    function spawnImage() {
        const img = document.createElement('img');
        img.src = images[currentIndex];

        // Cycle index
        currentIndex = (currentIndex + 1) % images.length;

        // Random Position
        // Keep away from center (where text is) roughly
        // Simple approach: random anywhere, but low opacity makes it okay
        const randomX = Math.random() * 90; // 0-90vw
        const randomY = Math.random() * 90; // 0-90vh

        // Initial State
        img.style.position = 'absolute';
        img.style.left = `${randomX}%`;
        img.style.top = `${randomY}%`;
        img.style.opacity = '0'; // Start invisible
        img.style.transform = 'scale(0.8) rotate(0deg)';
        img.style.transition = 'opacity 3s ease-in-out'; // Slow fade in
        img.style.maxWidth = '300px';
        img.style.borderRadius = '12px';
        img.style.boxShadow = '0 10px 30px rgba(0,0,0,0.3)';
        img.style.filter = 'grayscale(30%) brightness(0.7)'; // Dim it slightly

        // Animation
        // We'll use Web Animations API for the rotation to avoid complex CSS class management specific to each instance if we want random directions, 
        // but simpler to just add a class that spins.
        const duration = 60 + Math.random() * 60; // 60-120s rotation (Slower)
        const direction = Math.random() > 0.5 ? 1 : -1;

        img.animate([
            { transform: `scale(0.8) rotate(0deg)` },
            { transform: `scale(0.8) rotate(${360 * direction}deg)` }
        ], {
            duration: duration * 1000,
            iterations: Infinity,
            easing: 'linear'
        });

        container.appendChild(img);

        // Trigger Fade In
        requestAnimationFrame(() => {
            img.style.opacity = '0.15'; // max opacity (subtle)
        });

        // Remove old images to prevent memory leak if runs forever? 
        // Valid for a landing page to keep adding? Maybe limit to 15.
        if (container.children.length > 15) {
            // Remove the oldest image (first child)
            // But first child might be something else? No, we only append images.
            // wait... we appended container to body, we append img to container.
            const firstImg = container.querySelector('img');
            if (firstImg) {
                // Fade out then remove
                firstImg.style.transition = 'opacity 2s';
                firstImg.style.opacity = '0';
                setTimeout(() => {
                    if (firstImg.parentNode) firstImg.remove();
                }, 2000);
            }
        }
    }

    // Initial Spawn - Warm up with multiple images so it doesn't look empty
    for (let i = 0; i < 5; i++) {
        setTimeout(spawnImage, i * 200); // Rapid fire start
    }

    // Interval
    setInterval(spawnImage, 3000); // One new image every 3 seconds
}

// Ensure DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initBackground);
} else {
    initBackground();
}
