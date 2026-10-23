package com.activest.domain.auth;

import com.activest.domain.portfolio.Portfolio;
import com.activest.domain.portfolio.PortfolioRepository;
import com.activest.domain.strategy.StrategyReservation;
import com.activest.domain.strategy.StrategyReservationRepository;
import com.activest.domain.user.User;
import com.activest.domain.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * O nascimento de uma conta: o usuário e o que todo usuário tem desde o início — a carteira padrão e a
 * estratégia de reserva de emergência (por percentual, alvo 0,00%).
 *
 * <p>Um lugar só para os dois caminhos que criam usuário — cadastro com código e primeiro login pelo
 * Google —, para que o próximo item do nascimento não seja lembrado em um e esquecido no outro. O
 * usuário semeado pela V9 não passa por aqui: a carteira dele vem da própria V9, e a reserva da V12.</p>
 */
@Component
public class AccountFactory {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final StrategyReservationRepository strategyReservationRepository;

    public AccountFactory(UserRepository userRepository, PortfolioRepository portfolioRepository,
                          StrategyReservationRepository strategyReservationRepository) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.strategyReservationRepository = strategyReservationRepository;
    }

    @Transactional
    public User create(User created) {
        User user = userRepository.save(created);

        Portfolio portfolio = new Portfolio();
        portfolio.setName("Minha Carteira");
        portfolio.setUser(user);
        portfolioRepository.save(portfolio);

        strategyReservationRepository.save(StrategyReservation.defaultFor(user));
        return user;
    }
}
