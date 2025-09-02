# Chess React UI - Frontend

This is the React frontend for the Chess AI Game application, implementing the new modern UI as specified in the High-Level Design document.

## 🚀 Quick Start

### Prerequisites
- Node.js 18+ 
- npm or yarn

### Installation
```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## 🏗️ Architecture

### Technology Stack
- **React 18** - Latest React with Concurrent Features
- **TypeScript 5** - Type safety and better developer experience
- **Vite** - Fast build tool and dev server
- **Tailwind CSS** - Utility-first CSS framework
- **Zustand** - Lightweight state management
- **React Query** - Server state management and caching
- **STOMP.js** - WebSocket communication

### Project Structure
```
src/
├── components/          # React components
│   ├── ui/             # Reusable UI components
│   ├── ChessBoard/     # Chess board components
│   ├── GameControls/   # Game control components
│   └── AIPanel/        # AI system components
├── hooks/              # Custom React hooks
├── stores/             # Zustand state stores
├── types/              # TypeScript type definitions
├── utils/              # Utility functions
└── contexts/           # React contexts
```

## 🎯 Features

### ✅ Implemented
- [x] React + TypeScript + Vite setup
- [x] Basic component structure
- [x] Zustand state management
- [x] WebSocket integration with STOMP
- [x] PWA configuration
- [x] Error boundary
- [x] Performance monitoring
- [x] Responsive design with Tailwind CSS

### 🚧 In Progress
- [ ] ShadCN/UI component library integration
- [ ] Backend dual UI routing configuration
- [ ] Complete chess board functionality
- [ ] AI training management
- [ ] Pawn promotion dialog
- [ ] Opening book integration

## 🔧 Development

### Available Scripts
- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm run preview` - Preview production build
- `npm run lint` - Run ESLint
- `npm run format` - Format code with Prettier
- `npm run type-check` - Run TypeScript type checking
- `npm run test` - Run tests
- `npm run test:coverage` - Run tests with coverage

### Environment Variables
Create a `.env.local` file:
```env
VITE_API_URL=http://localhost:8081
VITE_WS_URL=ws://localhost:8081/ws
```

## 🌐 Deployment

The frontend is designed to be served at `/newage/chess/` path while maintaining the existing UI at the root path.

### Build Process
1. Run `npm run build`
2. Copy `dist/` contents to `src/main/resources/static/newage/chess/`
3. Backend serves the React app at `/newage/chess/**`

## 📱 PWA Features

- **Offline Support** - Service worker caches resources
- **Installable** - Can be installed as a native app
- **Background Sync** - Syncs game data when online
- **Push Notifications** - Training completion notifications

## 🧪 Testing

```bash
# Run unit tests
npm run test

# Run tests with coverage
npm run test:coverage

# Run E2E tests (when implemented)
npm run test:e2e
```

## 📊 Performance

- **Bundle Size**: < 2MB (gzipped)
- **Load Time**: < 3 seconds
- **Lighthouse Score**: > 90
- **Core Web Vitals**: Monitored and optimized

## 🔒 Security

- **Content Security Policy** - Configured for React
- **Input Sanitization** - All user inputs sanitized
- **XSS Protection** - Built-in React protections
- **HTTPS Only** - In production

## 📚 Documentation

- [High-Level Design](../docs/CURSOR_UI_REFACTOR.md)
- [API Documentation](../docs/API.md)
- [Component Documentation](./docs/components.md)

## 🤝 Contributing

1. Follow the existing code style
2. Write tests for new features
3. Update documentation
4. Ensure accessibility compliance
5. Test on multiple devices

## 📄 License

This project is part of the Chess AI Game application.
