import React from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { useTheme } from '@/context/ThemeContext';
import { clsx } from 'clsx';

export const Layout = () => {
  const { isDark } = useTheme();

  return (
    <div className={clsx(
      "flex h-screen transition-colors duration-300",
      isDark ? "bg-black" : "bg-[#f5f5f0]"
    )}>
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        <main className={clsx(
          "flex-1 overflow-x-hidden overflow-y-auto p-6 transition-colors duration-300",
          isDark ? "bg-black" : "bg-[#f5f5f0]"
        )}>
          <Outlet />
        </main>
      </div>
    </div>
  );
};
