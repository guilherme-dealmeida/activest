import React, { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';

export const OAuthCallback = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { login } = useAuth();

  useEffect(() => {
    const token = searchParams.get('token');
    
    const handleAuth = async () => {
      if (token) {
        try {
          await login(token);
          navigate('/dashboard');
        } catch (error) {
          console.error('Failed to login with token', error);
          navigate('/login');
        }
      } else {
        navigate('/login');
      }
    };

    handleAuth();
  }, [searchParams, login, navigate]);

  return (
    <div className="h-screen w-screen flex flex-col items-center justify-center bg-background">
      <LoadingSpinner className="h-8 w-8 text-accent mb-4" />
      <p className="text-gray-400">Autenticando...</p>
    </div>
  );
};
