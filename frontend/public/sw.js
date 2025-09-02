// Chess AI Game - Service Worker
// Advanced PWA with offline support, background sync, and push notifications

const CACHE_NAME = 'chess-ai-v1.0.0';
const STATIC_CACHE = 'chess-ai-static-v1.0.0';
const DYNAMIC_CACHE = 'chess-ai-dynamic-v1.0.0';

// Files to cache for offline functionality
const STATIC_FILES = [
  '/newage/chess/',
  '/newage/chess/index.html',
  '/newage/chess/manifest.json',
  '/newage/chess/static/js/bundle.js',
  '/newage/chess/static/css/main.css',
  '/newage/chess/assets/',
  // Add more static assets as needed
];

// API endpoints to cache
const API_CACHE_PATTERNS = [
  '/api/game-state',
  '/api/ai-status',
  '/api/training-status',
  '/api/mcp-status',
  '/api/openings'
];

// Install event - cache static files
self.addEventListener('install', (event) => {
  console.log('Service Worker: Installing...');
  
  event.waitUntil(
    caches.open(STATIC_CACHE)
      .then((cache) => {
        console.log('Service Worker: Caching static files');
        return cache.addAll(STATIC_FILES);
      })
      .then(() => {
        console.log('Service Worker: Static files cached');
        return self.skipWaiting();
      })
      .catch((error) => {
        console.error('Service Worker: Failed to cache static files', error);
      })
  );
});

// Activate event - clean up old caches
self.addEventListener('activate', (event) => {
  console.log('Service Worker: Activating...');
  
  event.waitUntil(
    caches.keys()
      .then((cacheNames) => {
        return Promise.all(
          cacheNames.map((cacheName) => {
            if (cacheName !== STATIC_CACHE && cacheName !== DYNAMIC_CACHE) {
              console.log('Service Worker: Deleting old cache', cacheName);
              return caches.delete(cacheName);
            }
          })
        );
      })
      .then(() => {
        console.log('Service Worker: Activated');
        return self.clients.claim();
      })
  );
});

// Fetch event - serve from cache or network
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);
  
  // Skip non-GET requests
  if (request.method !== 'GET') {
    return;
  }
  
  // Handle different types of requests
  if (url.pathname.startsWith('/newage/chess/')) {
    // Handle React app requests
    event.respondWith(handleReactAppRequest(request));
  } else if (url.pathname.startsWith('/api/')) {
    // Handle API requests
    event.respondWith(handleAPIRequest(request));
  } else if (url.pathname.startsWith('/ws')) {
    // Skip WebSocket requests
    return;
  } else {
    // Handle other requests
    event.respondWith(handleOtherRequest(request));
  }
});

// Handle React app requests
async function handleReactAppRequest(request) {
  try {
    // Try cache first
    const cachedResponse = await caches.match(request);
    if (cachedResponse) {
      return cachedResponse;
    }
    
    // Try network
    const networkResponse = await fetch(request);
    
    // Cache successful responses
    if (networkResponse.ok) {
      const cache = await caches.open(STATIC_CACHE);
      cache.put(request, networkResponse.clone());
    }
    
    return networkResponse;
  } catch (error) {
    console.error('Service Worker: Failed to fetch React app resource', error);
    
    // Return offline page for navigation requests
    if (request.mode === 'navigate') {
      const offlinePage = await caches.match('/newage/chess/index.html');
      return offlinePage || new Response('Offline', { status: 503 });
    }
    
    throw error;
  }
}

// Handle API requests
async function handleAPIRequest(request) {
  try {
    // Try network first for API requests
    const networkResponse = await fetch(request);
    
    // Cache successful GET responses
    if (networkResponse.ok && request.method === 'GET') {
      const cache = await caches.open(DYNAMIC_CACHE);
      cache.put(request, networkResponse.clone());
    }
    
    return networkResponse;
  } catch (error) {
    console.error('Service Worker: API request failed', error);
    
    // Try to serve from cache
    const cachedResponse = await caches.match(request);
    if (cachedResponse) {
      return cachedResponse;
    }
    
    // Return offline response for API requests
    return new Response(
      JSON.stringify({ 
        error: 'Offline', 
        message: 'No internet connection available' 
      }),
      { 
        status: 503,
        headers: { 'Content-Type': 'application/json' }
      }
    );
  }
}

// Handle other requests
async function handleOtherRequest(request) {
  try {
    return await fetch(request);
  } catch (error) {
    console.error('Service Worker: Request failed', error);
    throw error;
  }
}

// Background sync for offline actions
self.addEventListener('sync', (event) => {
  console.log('Service Worker: Background sync triggered', event.tag);
  
  switch (event.tag) {
    case 'chess-moves':
      event.waitUntil(syncChessMoves());
      break;
    case 'training-data':
      event.waitUntil(syncTrainingData());
      break;
    case 'game-state':
      event.waitUntil(syncGameState());
      break;
    default:
      console.log('Service Worker: Unknown sync tag', event.tag);
  }
});

// Sync chess moves when back online
async function syncChessMoves() {
  try {
    // Get pending moves from IndexedDB
    const pendingMoves = await getPendingMoves();
    
    for (const move of pendingMoves) {
      try {
        const response = await fetch('/api/make-move', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(move)
        });
        
        if (response.ok) {
          // Remove from pending moves
          await removePendingMove(move.id);
        }
      } catch (error) {
        console.error('Service Worker: Failed to sync move', error);
      }
    }
  } catch (error) {
    console.error('Service Worker: Failed to sync chess moves', error);
  }
}

// Sync training data when back online
async function syncTrainingData() {
  try {
    // Get pending training data from IndexedDB
    const pendingData = await getPendingTrainingData();
    
    for (const data of pendingData) {
      try {
        const response = await fetch('/api/training', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(data)
        });
        
        if (response.ok) {
          // Remove from pending data
          await removePendingTrainingData(data.id);
        }
      } catch (error) {
        console.error('Service Worker: Failed to sync training data', error);
      }
    }
  } catch (error) {
    console.error('Service Worker: Failed to sync training data', error);
  }
}

// Sync game state when back online
async function syncGameState() {
  try {
    // Get pending game state from IndexedDB
    const pendingState = await getPendingGameState();
    
    if (pendingState) {
      try {
        const response = await fetch('/api/game-state', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(pendingState)
        });
        
        if (response.ok) {
          // Clear pending state
          await clearPendingGameState();
        }
      } catch (error) {
        console.error('Service Worker: Failed to sync game state', error);
      }
    }
  } catch (error) {
    console.error('Service Worker: Failed to sync game state', error);
  }
}

// Push notification handling
self.addEventListener('push', (event) => {
  console.log('Service Worker: Push notification received');
  
  const options = {
    body: 'Chess AI Game notification',
    icon: '/pwa-192x192.png',
    badge: '/pwa-192x192.png',
    vibrate: [100, 50, 100],
    data: {
      dateOfArrival: Date.now(),
      primaryKey: 1
    },
    actions: [
      {
        action: 'explore',
        title: 'Open Game',
        icon: '/pwa-192x192.png'
      },
      {
        action: 'close',
        title: 'Close',
        icon: '/pwa-192x192.png'
      }
    ]
  };
  
  if (event.data) {
    const data = event.data.json();
    options.body = data.body || options.body;
    options.data = { ...options.data, ...data };
  }
  
  event.waitUntil(
    self.registration.showNotification('Chess AI Game', options)
  );
});

// Notification click handling
self.addEventListener('notificationclick', (event) => {
  console.log('Service Worker: Notification clicked');
  
  event.notification.close();
  
  if (event.action === 'explore') {
    event.waitUntil(
      clients.openWindow('/newage/chess/')
    );
  } else if (event.action === 'close') {
    // Just close the notification
    return;
  } else {
    // Default action - open the app
    event.waitUntil(
      clients.openWindow('/newage/chess/')
    );
  }
});

// Helper functions for IndexedDB operations
async function getPendingMoves() {
  // TODO: Implement IndexedDB operations
  return [];
}

async function removePendingMove(id) {
  // TODO: Implement IndexedDB operations
}

async function getPendingTrainingData() {
  // TODO: Implement IndexedDB operations
  return [];
}

async function removePendingTrainingData(id) {
  // TODO: Implement IndexedDB operations
}

async function getPendingGameState() {
  // TODO: Implement IndexedDB operations
  return null;
}

async function clearPendingGameState() {
  // TODO: Implement IndexedDB operations
}

// Message handling for communication with main thread
self.addEventListener('message', (event) => {
  console.log('Service Worker: Message received', event.data);
  
  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
  
  if (event.data && event.data.type === 'GET_VERSION') {
    event.ports[0].postMessage({ version: CACHE_NAME });
  }
});

console.log('Service Worker: Loaded successfully');
