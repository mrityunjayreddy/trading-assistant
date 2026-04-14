import { Component } from 'react';

export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, message: '' };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, message: error?.message || 'Unexpected error' };
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="error-boundary">
          <span className="error-boundary__icon">⚠</span>
          <p className="error-boundary__title">{this.props.title || 'Something went wrong'}</p>
          <p className="error-boundary__message">{this.state.message}</p>
        </div>
      );
    }
    return this.props.children;
  }
}
